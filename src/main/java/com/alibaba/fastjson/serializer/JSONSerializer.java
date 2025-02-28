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
import java.io.Writer;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.util.FieldInfo;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class JSONSerializer extends SerializeFilterable {

    private final SerializeConfig                    config;
    public final SerializeWriter                     out;

    private int                                      indentCount = 0;
    private String                                   indent      = "\t";

    private String                                   dateFormatPattern;
    private DateFormat                               dateFormat;

    protected IdentityHashMap<Object, SerialContext> references  = null;
    protected SerialContext                          context;

    protected TimeZone                               timeZone    = JSON.defaultTimeZone;
    protected Locale                                 locale      = JSON.defaultLocale;

    public JSONSerializer(){
        this(new SerializeWriter(), SerializeConfig.getGlobalInstance());
    }

    public JSONSerializer(SerializeWriter out){
        this(out, SerializeConfig.getGlobalInstance());
    }

    public JSONSerializer(SerializeConfig config){
        this(new SerializeWriter(), config);
    }

    public JSONSerializer(SerializeWriter out, SerializeConfig config){
        this.out = out;
        this.config = config;
    }

    public String getDateFormatPattern() {
        if (dateFormat instanceof SimpleDateFormat) {
            return ((SimpleDateFormat) dateFormat).toPattern();
        }
        return dateFormatPattern;
    }

    public DateFormat getDateFormat() {
        if (dateFormat == null) {
            if (dateFormatPattern != null) {
                dateFormat = new SimpleDateFormat(dateFormatPattern, locale);
                dateFormat.setTimeZone(timeZone);
            }
        }

        return dateFormat;
    }

    public void setDateFormat(DateFormat dateFormat) {
        this.dateFormat = dateFormat;
        if (dateFormatPattern != null) {
            dateFormatPattern = null;
        }
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormatPattern = dateFormat;
        if (this.dateFormat != null) {
            this.dateFormat = null;
        }
    }

    public SerialContext getContext() {
        return context;
    }

    public void setContext(SerialContext context) {
        this.context = context;
    }
    
    public void setContext(SerialContext parent, Object object, Object fieldName, int features) {
        this.setContext(parent, object, fieldName, features, 0);
    }
    
    public void setContext(SerialContext parent, Object object, Object fieldName, int features, int fieldFeatures) {
        if (out.disableCircularReferenceDetect) {
            return;
        }

        this.context = new SerialContext(parent, object, fieldName, features, fieldFeatures);
        if (references == null) {
            references = new IdentityHashMap<Object, SerialContext>();
        }
        this.references.put(object, context);
    }

    public void setContext(Object object, Object fieldName) {
        this.setContext(context, object, fieldName, 0);
    }

    public void popContext() {
        if (context != null) {
            this.context = this.context.parent;
        }
    }
    
    public final boolean isWriteClassName(Type fieldType, Object obj) {
        return out.isEnabled(SerializerFeature.WriteClassName) //
               && (fieldType != null //
                   || (!out.isEnabled(SerializerFeature.NotWriteRootClassName)) //
                   || context.parent != null);
    }

    public boolean containsReference(Object value) {
        return references != null && references.containsKey(value);
    }

    public void writeReference(Object object) {
        SerialContext context = this.context;
        Object current = context.object;

        if (object == current) {
            out.write("{\"$ref\":\"@\"}");
            return;
        }

        SerialContext parentContext = context.parent;

        if (parentContext != null) {
            if (object == parentContext.object) {
                out.write("{\"$ref\":\"..\"}");
                return;
            }
        }

        SerialContext rootContext = context;
        for (;;) {
            if (rootContext.parent == null) {
                break;
            }
            rootContext = rootContext.parent;
        }

        if (object == rootContext.object) {
            out.write("{\"$ref\":\"$\"}");
        } else {
            out.write("{\"$ref\":\"");
            out.write(references.get(object).toString());
            out.write("\"}");
        }
    }
    
    public boolean checkValue() {
        return (valueFilters != null && valueFilters.size() > 0) //
               || (contextValueFilters != null && contextValueFilters.size() > 0)
               || out.writeNonStringValueAsString;
    }
    
    public int getIndentCount() {
        return indentCount;
    }

    public void incrementIndent() {
        indentCount++;
    }

    public void decrementIdent() {
        indentCount--;
    }

    public void println() {
        out.write('\n');
        for (int i = 0; i < indentCount; ++i) {
            out.write(indent);
        }
    }

    public SerializeWriter getWriter() {
        return out;
    }

    public String toString() {
        return out.toString();
    }

    public void config(SerializerFeature feature, boolean state) {
        out.config(feature, state);
    }

    public boolean isEnabled(SerializerFeature feature) {
        return out.isEnabled(feature);
    }

    public void writeNull() {
        this.out.writeNull();
    }

    public SerializeConfig getMapping() {
        return config;
    }

    public static void write(Writer out, Object object) {
        SerializeWriter writer = new SerializeWriter();
        try {
            JSONSerializer serializer = new JSONSerializer(writer);
            serializer.write(object);
            writer.writeTo(out);
        } catch (IOException ex) {
            throw new JSONException(ex.getMessage(), ex);
        } finally {
            writer.close();
        }
    }

    public static void write(SerializeWriter out, Object object) {
        JSONSerializer serializer = new JSONSerializer(out);
        serializer.write(object);
    }

    public final void write(Object object) {
        if (object == null) {
            out.writeNull();
            return;
        }
        
        Class<?> clazz = object.getClass();
        ObjectSerializer writer = getObjectWriter(clazz);

        try {
            writer.write(this, object, null, null, 0);
        } catch (IOException e) {
            throw new JSONException(e.getMessage(), e);
        }
    }

    public final void writeWithFieldName(Object object, Object fieldName) {
        writeWithFieldName(object, fieldName, null, 0);
    }

    protected final void writeKeyValue(char seperator, String key, Object value) {
        if (seperator != '\0') {
            out.write(seperator);
        }
        out.writeFieldName(key);
        write(value);
    }

    public final void writeWithFieldName(Object object, Object fieldName, Type fieldType, int fieldFeatures) {
        try {
            if (object == null) {
                out.writeNull();
                return;
            }

            Class<?> clazz = object.getClass();

            ObjectSerializer writer = getObjectWriter(clazz);

            writer.write(this, object, fieldName, fieldType, fieldFeatures);
        } catch (IOException e) {
            throw new JSONException(e.getMessage(), e);
        }
    }

    public final void writeWithFormat(Object object, String format) {
        if (object instanceof Date) {
            DateFormat dateFormat = this.getDateFormat();
            if (dateFormat == null) {
                dateFormat = new SimpleDateFormat(format, locale);
                dateFormat.setTimeZone(timeZone);
            }
            String text = dateFormat.format((Date) object);
            out.writeString(text);
            return;
        }
        write(object);
    }

    public final void write(String text) {
        StringCodec.instance.write(this, text);
    }

    public ObjectSerializer getObjectWriter(Class<?> clazz) {
        return config.getObjectWriter(clazz);
    }

    public void close() {
        this.out.close();
    }

    /**
     * only invoke by asm byte
     * @return
     */
    public boolean writeDirect(JavaBeanSerializer javaBeanDeser) {
        return out.writeDirect // 
                && this.writeDirect
                && javaBeanDeser.writeDirect
                ;
    }
    
    public FieldInfo getFieldInfo() {
        return null;
    }
    
    public Object processValue(SerializeFilterable javaBeanDeser, //
                                      Object object, // 
                                      String key, // 
                                      Object propertyValue) {
        
        if (propertyValue != null // 
                && out.writeNonStringValueAsString) {
            if (propertyValue instanceof Number || propertyValue instanceof Boolean) {
                propertyValue = propertyValue.toString();
            }
        }
        
        List<ValueFilter> valueFilters = this.valueFilters;
        if (valueFilters != null) {
            for (ValueFilter valueFilter : valueFilters) {
                propertyValue = valueFilter.process(object, key, propertyValue);
            }
        }
        
        if (javaBeanDeser.valueFilters != null) {
            for (ValueFilter valueFilter : javaBeanDeser.valueFilters) {
                propertyValue = valueFilter.process(object, key, propertyValue);
            }
        }
        
        if (this.contextValueFilters != null) {
            BeanContext fieldContext = javaBeanDeser.getBeanContext(key);
            for (ContextValueFilter valueFilter : this.contextValueFilters) {
                propertyValue = valueFilter.process(fieldContext, object, key, propertyValue);
            }
        }
        
        if (javaBeanDeser.contextValueFilters != null) {
            BeanContext fieldContext = javaBeanDeser.getBeanContext(key);
            for (ContextValueFilter valueFilter : javaBeanDeser.contextValueFilters) {
                propertyValue = valueFilter.process(fieldContext, object, key, propertyValue);
            }
        }

        return propertyValue;
    }
    
    public String processKey(SerializeFilterable javaBeanDeser, //
                             Object object, // 
                             String key, // 
                             Object propertyValue) {
        if (this.nameFilters != null) {
            for (NameFilter nameFilter : this.nameFilters) {
                key = nameFilter.process(object, key, propertyValue);
            }
        }
        
        if (javaBeanDeser.nameFilters != null) {
            for (NameFilter nameFilter : javaBeanDeser.nameFilters) {
                key = nameFilter.process(object, key, propertyValue);
            }
        }

        return key;
    }
    
    public boolean applyName(Object object, String key) {
        List<PropertyPreFilter> filters = this.propertyPreFilters;

        if (filters == null) {
            return true;
        }

        for (PropertyPreFilter filter : filters) {
            if (!filter.apply(this, object, key)) {
                return false;
            }
        }

        return true;
    }
    
    public boolean apply(Object object, String key, Object propertyValue) {
        List<PropertyFilter> propertyFilters = this.propertyFilters;

        if (propertyFilters == null) {
            return true;
        }

        for (PropertyFilter propertyFilter : propertyFilters) {
            if (!propertyFilter.apply(object, key, propertyValue)) {
                return false;
            }
        }

        return true;
    }
    
    public char writeBefore(Object object, char seperator) {
        List<BeforeFilter> beforeFilters = this.beforeFilters;
        if (beforeFilters != null) {
            for (BeforeFilter beforeFilter : beforeFilters) {
                seperator = beforeFilter.writeBefore(this, object, seperator);
            }
        }
        return seperator;
    }
    
    public char writeAfter(Object object, char seperator) {
        List<AfterFilter> afterFilters = this.afterFilters;
        if (afterFilters != null) {
            for (AfterFilter afterFilter : afterFilters) {
                seperator = afterFilter.writeAfter(this, object, seperator);
            }
        }
        return seperator;
    }
    
    public boolean applyLabel(String label) {
        List<LabelFilter> labelFilters = this.labelFilters;

        if (labelFilters != null) {
            boolean apply = true;

            for (LabelFilter propertyFilter : labelFilters) {
                if (!propertyFilter.apply(label)) {
                    return false;
                }
            }

            return apply;
        }

        return true;
    }
    
    
}
