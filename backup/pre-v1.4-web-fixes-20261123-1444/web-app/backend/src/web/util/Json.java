package web.util;

import java.lang.reflect.Method;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer. Kept in the project so no extra library or Maven
 * dependency is required. It handles the JSON shapes used by the EquipLink API.
 */
public final class Json {

    private Json() {
    }

    // ---------------- parsing ----------------

    public static Object parse(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Unexpected trailing JSON at position " + parser.position());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, entry) -> result.put(String.valueOf(key), entry));
            return result;
        }
        return new LinkedHashMap<>();
    }

    public static List<Object> array(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return new ArrayList<>();
    }

    public static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String text(Map<String, Object> map, String key) {
        return string(map.get(key));
    }

    public static long number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(string(value).trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public static int integer(Map<String, Object> map, String key) {
        return (int) number(map, key);
    }

    public static boolean flag(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(string(value))
                || "1".equals(string(value));
    }

    private static final class Parser {
        private final String source;
        private int index;

        Parser(String source) {
            this.source = source;
        }

        int position() {
            return index;
        }

        boolean atEnd() {
            return index >= source.length();
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        Object readValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON.");
            }
            char current = source.charAt(index);
            return switch (current) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            index++; // {
            skipWhitespace();
            if (!atEnd() && source.charAt(index) == '}') {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || source.charAt(index) != '"') {
                    throw new IllegalArgumentException("Expected JSON property name at " + index + ".");
                }
                String key = readString();
                skipWhitespace();
                if (atEnd() || source.charAt(index) != ':') {
                    throw new IllegalArgumentException("Expected ':' at " + index + ".");
                }
                index++;
                result.put(key, readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new IllegalArgumentException("Unterminated JSON object.");
                }
                char separator = source.charAt(index++);
                if (separator == '}') {
                    return result;
                }
                if (separator != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' at " + (index - 1) + ".");
                }
            }
        }

        private List<Object> readArray() {
            List<Object> result = new ArrayList<>();
            index++; // [
            skipWhitespace();
            if (!atEnd() && source.charAt(index) == ']') {
                index++;
                return result;
            }
            while (true) {
                result.add(readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new IllegalArgumentException("Unterminated JSON array.");
                }
                char separator = source.charAt(index++);
                if (separator == ']') {
                    return result;
                }
                if (separator != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' at " + (index - 1) + ".");
                }
            }
        }

        private String readString() {
            index++; // opening quote
            StringBuilder value = new StringBuilder();
            while (!atEnd()) {
                char current = source.charAt(index++);
                if (current == '"') {
                    return value.toString();
                }
                if (current != '\\') {
                    value.append(current);
                    continue;
                }
                if (atEnd()) {
                    break;
                }
                char escape = source.charAt(index++);
                switch (escape) {
                    case '"', '\\', '/' -> value.append(escape);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape.");
                        }
                        String hex = source.substring(index, index + 4);
                        index += 4;
                        value.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw new IllegalArgumentException("Invalid JSON escape: " + escape);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string.");
        }

        private Object readLiteral(String literal, Object value) {
            if (!source.startsWith(literal, index)) {
                throw new IllegalArgumentException("Invalid JSON literal at " + index + ".");
            }
            index += literal.length();
            return value;
        }

        private Object readNumber() {
            int start = index;
            if (source.charAt(index) == '-' || source.charAt(index) == '+') {
                index++;
            }
            while (!atEnd() && (Character.isDigit(source.charAt(index))
                    || source.charAt(index) == '.' || source.charAt(index) == 'e'
                    || source.charAt(index) == 'E' || source.charAt(index) == '-'
                    || source.charAt(index) == '+')) {
                index++;
            }
            String raw = source.substring(start, index);
            try {
                if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                    return Double.parseDouble(raw);
                }
                return Long.parseLong(raw);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid JSON number: " + raw);
            }
        }
    }

    // ---------------- writing ----------------

    public static String write(Object value) {
        StringBuilder output = new StringBuilder();
        append(output, value, 0);
        return output.toString();
    }

    private static void append(StringBuilder output, Object value, int depth) {
        if (depth > 12) {
            output.append("null");
            return;
        }
        if (value == null) {
            output.append("null");
        } else if (value instanceof String string) {
            appendString(output, string);
        } else if (value instanceof Boolean || value instanceof Number) {
            output.append(value);
        } else if (value instanceof Temporal || value instanceof java.util.Date
                || value instanceof Enum<?>) {
            appendString(output, String.valueOf(value));
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                appendString(output, String.valueOf(entry.getKey()));
                output.append(':');
                append(output, entry.getValue(), depth + 1);
            }
            output.append('}');
        } else if (value instanceof Collection<?> collection) {
            output.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                append(output, item, depth + 1);
            }
            output.append(']');
        } else if (value instanceof Object[] array) {
            output.append('[');
            for (int i = 0; i < array.length; i++) {
                if (i > 0) {
                    output.append(',');
                }
                append(output, array[i], depth + 1);
            }
            output.append(']');
        } else if (value instanceof byte[]) {
            // Binary data is served by a dedicated endpoint, never embedded in JSON.
            output.append("null");
        } else {
            appendBean(output, value, depth);
        }
    }

    private static void appendBean(StringBuilder output, Object value, int depth) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Method method : value.getClass().getMethods()) {
            String name = method.getName();
            boolean getter = (name.startsWith("get") && name.length() > 3)
                    || (name.startsWith("is") && name.length() > 2
                            && (method.getReturnType() == boolean.class
                            || method.getReturnType() == Boolean.class));
            if (!getter || method.getParameterCount() != 0
                    || method.getDeclaringClass() == Object.class
                    || name.equals("getClass")) {
                continue;
            }
            try {
                String key = name.startsWith("is")
                        ? name.substring(2)
                        : name.substring(3);
                key = Character.toLowerCase(key.charAt(0)) + key.substring(1);
                properties.put(key, method.invoke(value));
            } catch (ReflectiveOperationException ignored) {
                // Skip properties that cannot be read.
            }
        }
        append(output, properties, depth + 1);
    }

    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                default -> {
                    if (current < 0x20) {
                        output.append(String.format("\\u%04x", (int) current));
                    } else {
                        output.append(current);
                    }
                }
            }
        }
        output.append('"');
    }
}
