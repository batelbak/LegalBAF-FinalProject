package ac.qbaf.legalsim.llm;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON Schema ממחלקת record של Java, כדי שהמודל המקומי יחזיר בדיוק את המבנה הזה (structured outputs של Ollama).
 * תומך במה שהפרויקט צריך: String, מספרים, boolean, List ו-record מקונן.
 * התיאור של כל שדה נלקח מ-@JsonPropertyDescription, כמו ב-SDK של Claude.
 */
public final class JsonSchemas {

    public static Map<String, Object> of(Class<?> type) {
        return schema(type);
    }

    private static Map<String, Object> schema(Type type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (type instanceof ParameterizedType p && p.getRawType() == List.class) {
            schema.put("type", "array");
            schema.put("items", schema(p.getActualTypeArguments()[0]));
            return schema;
        }
        if (!(type instanceof Class<?> c)) {
            throw new IllegalArgumentException("Unsupported type for a JSON schema: " + type);
        }
        if (c == String.class) {
            schema.put("type", "string");
        } else if (c == double.class || c == Double.class || c == float.class || c == Float.class) {
            schema.put("type", "number");
        } else if (c == int.class || c == Integer.class || c == long.class || c == Long.class) {
            schema.put("type", "integer");
        } else if (c == boolean.class || c == Boolean.class) {
            schema.put("type", "boolean");
        } else if (c.isRecord()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            for (RecordComponent component : c.getRecordComponents()) {
                Map<String, Object> property = schema(component.getGenericType());
                JsonPropertyDescription description = description(c, component);
                if (description != null) {
                    property.put("description", description.value());
                }
                properties.put(component.getName(), property);
                required.add(component.getName());
            }
            schema.put("type", "object");
            schema.put("properties", properties);
            schema.put("required", required);
            schema.put("additionalProperties", false);
        } else {
            throw new IllegalArgumentException("Unsupported type for a JSON schema: " + c.getName());
        }
        return schema;
    }

    /**
     * האנוטציה לא מוגדרת עם יעד RECORD_COMPONENT, ולכן Java מעביר אותה לשדה ול-accessor שנוצרים מהרכיב,
     * ולא לרכיב עצמו.
     */
    private static JsonPropertyDescription description(Class<?> record, RecordComponent component) {
        JsonPropertyDescription description = component.getAccessor().getAnnotation(JsonPropertyDescription.class);
        if (description == null) {
            try {
                description = record.getDeclaredField(component.getName()).getAnnotation(JsonPropertyDescription.class);
            } catch (NoSuchFieldException e) {
                return null;
            }
        }
        return description;
    }

    private JsonSchemas() {
    }
}
