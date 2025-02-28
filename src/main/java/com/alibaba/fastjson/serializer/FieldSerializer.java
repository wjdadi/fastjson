/*
 * Copyright 1999-2101 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.fastjson.serializer;

import java.io.IOException;
import java.lang.reflect.Member;
import java.util.Collection;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.util.FieldInfo;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class FieldSerializer implements Comparable<FieldSerializer> {

    public final FieldInfo        fieldInfo;
    protected final boolean       writeNull;
    protected int                 features;

    private final String          double_quoted_fieldPrefix;
    private String                single_quoted_fieldPrefix;
    private String                un_quoted_fieldPrefix;

    protected BeanContext         fieldContext;

    private String                format;
    protected boolean             writeNumberAsZero       = false;
    protected boolean             writeNullStringAsEmpty  = false;
    protected boolean             writeNullBooleanAsFalse = false;
    protected boolean             writeNullListAsEmpty    = false;
    protected boolean             writeEnumUsingToString  = false;
    protected boolean             writeEnumUsingName      = false;

    private RuntimeSerializerInfo runtimeInfo;
    
    public FieldSerializer(Class<?> beanType, FieldInfo fieldInfo){
        this.fieldInfo = fieldInfo;
        this.fieldContext = new BeanContext(beanType, fieldInfo);
        
        fieldInfo.setAccessible();

        this.double_quoted_fieldPrefix = '"' + fieldInfo.name + "\":";

        boolean writeNull = false;
        JSONField annotation = fieldInfo.getAnnotation();
        if (annotation != null) {
            for (SerializerFeature feature : annotation.serialzeFeatures()) {
                if (feature == SerializerFeature.WriteMapNullValue) {
                    writeNull = true;
                    break;
                }
            }
            
            format = annotation.format();

            if (format.trim().length() == 0) {
                format = null;
            }

            for (SerializerFeature feature : annotation.serialzeFeatures()) {
                if (feature == SerializerFeature.WriteNullNumberAsZero) {
                    writeNumberAsZero = true;
                } else if (feature == SerializerFeature.WriteNullStringAsEmpty) {
                    writeNullStringAsEmpty = true;
                } else if (feature == SerializerFeature.WriteNullBooleanAsFalse) {
                    writeNullBooleanAsFalse = true;
                } else if (feature == SerializerFeature.WriteNullListAsEmpty) {
                    writeNullListAsEmpty = true;
                } else if (feature == SerializerFeature.WriteEnumUsingToString) {
                    writeEnumUsingToString = true;
                }else if(feature == SerializerFeature.WriteEnumUsingName){
                    writeEnumUsingName = true;
                }
            }
            
            features = SerializerFeature.of(annotation.serialzeFeatures());
        }
        
        this.writeNull = writeNull;
    }

    public void writePrefix(JSONSerializer serializer) throws IOException {
        SerializeWriter out = serializer.out;

        if (out.quoteFieldNames) {
            if (out.useSingleQuotes) {
                if (single_quoted_fieldPrefix == null) {
                    single_quoted_fieldPrefix = '\'' + fieldInfo.name + "\':";
                }
                out.write(single_quoted_fieldPrefix);
            } else {
                out.write(double_quoted_fieldPrefix);
            }
        } else {
            if (un_quoted_fieldPrefix == null) {
                this.un_quoted_fieldPrefix = fieldInfo.name + ":";
            }
            out.write(un_quoted_fieldPrefix);
        }
    }

    public Object getPropertyValue(Object object) throws Exception {
        try {
            return fieldInfo.get(object);
        } catch (Exception ex) {
            Member member =  fieldInfo.getMember();
            String qualifiedName = member.getDeclaringClass().getName() + "." + member.getName();
            
            throw new JSONException("get property error。 " + qualifiedName, ex);
        }
    }


    
    public int compareTo(FieldSerializer o) {
        return this.fieldInfo.compareTo(o.fieldInfo);
    }
    

    public void writeValue(JSONSerializer serializer, Object propertyValue) throws Exception {
        if (format != null) {
            serializer.writeWithFormat(propertyValue, format);
            return;
        }

        if (runtimeInfo == null) {

            Class<?> runtimeFieldClass;
            if (propertyValue == null) {
                runtimeFieldClass = this.fieldInfo.fieldClass;
            } else {
                runtimeFieldClass = propertyValue.getClass();
            }

            ObjectSerializer fieldSerializer = serializer.getObjectWriter(runtimeFieldClass);
            runtimeInfo = new RuntimeSerializerInfo(fieldSerializer, runtimeFieldClass);
        }
        
        final RuntimeSerializerInfo runtimeInfo = this.runtimeInfo;
        
        final int fieldFeatures = fieldInfo.serialzeFeatures;

        if (propertyValue == null) {
            Class<?> runtimeFieldClass = runtimeInfo.runtimeFieldClass;
            SerializeWriter out  = serializer.out;
            boolean writeNumberAsZero = this.writeNumberAsZero || (out.features & SerializerFeature.WriteNullNumberAsZero.mask) != 0;
            if (writeNumberAsZero && Number.class.isAssignableFrom(runtimeFieldClass)) {
                out.write('0');
                return;
            } else if (writeNullStringAsEmpty && String.class == runtimeFieldClass) {
                out.write("\"\"");
                return;
            } else if (writeNullBooleanAsFalse && Boolean.class == runtimeFieldClass) {
                out.write("false");
                return;
            } else if (writeNullListAsEmpty && Collection.class.isAssignableFrom(runtimeFieldClass)) {
                out.write("[]");
                return;
            }

            ObjectSerializer fieldSerializer = runtimeInfo.fieldSerializer;
            if ((out.writeMapNullValue) 
                    && fieldSerializer instanceof ASMJavaBeanSerializer) {
                out.writeNull();
                return;
            }
            
            fieldSerializer.write(serializer, null, fieldInfo.name, fieldInfo.fieldType, fieldFeatures);
            return;
        }

        if (fieldInfo.isEnum) {
            if (writeEnumUsingName) {
                serializer.out.writeString(((Enum<?>) propertyValue).name());
                return;
            }

            if (writeEnumUsingToString) {
                serializer.out.writeString(((Enum<?>) propertyValue).toString());
                return;
            }
        }
        
        Class<?> valueClass = propertyValue.getClass();
        if (valueClass == runtimeInfo.runtimeFieldClass) {
            runtimeInfo.fieldSerializer.write(serializer, propertyValue, fieldInfo.name, fieldInfo.fieldType, fieldFeatures);
            return;
        }

        ObjectSerializer valueSerializer = serializer.getObjectWriter(valueClass);
        valueSerializer.write(serializer, propertyValue, fieldInfo.name, fieldInfo.fieldType, fieldFeatures);
    }

    static class RuntimeSerializerInfo {
        ObjectSerializer fieldSerializer;
        Class<?>         runtimeFieldClass;

        public RuntimeSerializerInfo(ObjectSerializer fieldSerializer, Class<?> runtimeFieldClass){
            this.fieldSerializer = fieldSerializer;
            this.runtimeFieldClass = runtimeFieldClass;
        }
    }
}
