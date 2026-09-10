package app.xagefixer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonNormalizer {
    private static final String WRAPPER_TYPE = "TweetWithVisibilityResults";
    private static final String TWEET_TYPE = "Tweet";
    private static final String MARKER = WRAPPER_TYPE;

    private JsonNormalizer() {}

    static String normalize(String body) {
        if (body == null || body.indexOf(MARKER) < 0) {
            return body;
        }

        try {
            Object parsed = new Parser(body).parseDocument();
            VisitResult result = visit(parsed);
            return result.changed ? Serializer.write(result.value) : body;
        } catch (Throwable ignored) {
            return body;
        }
    }

    private static VisitResult visit(Object current) {
        if (current instanceof List<?>) {
            List<Object> list = castList(current);
            boolean changed = false;
            for (int index = 0; index < list.size(); index += 1) {
                Object child = list.get(index);
                VisitResult next = visit(child);
                if (next.value != child) {
                    list.set(index, next.value);
                    changed = true;
                }
                changed |= next.changed;
            }
            return new VisitResult(list, changed);
        }

        if (!(current instanceof Map<?, ?>)) {
            return new VisitResult(current, false);
        }

        Map<String, Object> object = castMap(current);
        Object wrappedTweet = object.get("tweet");
        if (WRAPPER_TYPE.equals(object.get("__typename")) && isTruthy(wrappedTweet)) {
            VisitResult tweetResult = visit(wrappedTweet);
            Object tweet = tweetResult.value;

            if (tweet instanceof Map<?, ?>) {
                Map<String, Object> tweetObject = castMap(tweet);
                Object tweetType = tweetObject.get("__typename");
                if (!isTruthy(tweetType) || WRAPPER_TYPE.equals(tweetType)) {
                    tweetObject.put("__typename", TWEET_TYPE);
                }

                Object legacy = tweetObject.get("legacy");
                if (legacy instanceof Map<?, ?>) {
                    Map<String, Object> legacyObject = castMap(legacy);
                    if (Boolean.FALSE.equals(legacyObject.get("possibly_sensitive_editable"))) {
                        legacyObject.put("possibly_sensitive_editable", Boolean.TRUE);
                    }
                }
            }

            return new VisitResult(tweet, true);
        }

        boolean changed = false;
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            Object child = entry.getValue();
            VisitResult next = visit(child);
            if (next.value != child) {
                entry.setValue(next.value);
                changed = true;
            }
            changed |= next.changed;
        }
        return new VisitResult(object, changed);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object value) {
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof NumberValue) {
            try {
                return Double.parseDouble(((NumberValue) value).raw) != 0.0d;
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        if (value instanceof String) {
            return !((String) value).isEmpty();
        }
        return true;
    }

    private static final class VisitResult {
        private final Object value;
        private final boolean changed;

        private VisitResult(Object value, boolean changed) {
            this.value = value;
            this.changed = changed;
        }
    }

    private static final class NumberValue {
        private final String raw;

        private NumberValue(String raw) {
            this.raw = raw;
        }
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            this.input = input;
        }

        private Object parseDocument() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (position != input.length()) {
                throw error("Trailing JSON content");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (position >= input.length()) {
                throw error("Expected JSON value");
            }

            char current = input.charAt(position);
            switch (current) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    consumeLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    consumeLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    consumeLiteral("null");
                    return null;
                default:
                    if (current == '-' || (current >= '0' && current <= '9')) {
                        return parseNumber();
                    }
                    throw error("Unexpected JSON value");
            }
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (consumeIf('}')) {
                return object;
            }

            while (true) {
                skipWhitespace();
                if (position >= input.length() || input.charAt(position) != '"') {
                    throw error("Expected object key");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (consumeIf('}')) {
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (consumeIf(']')) {
                return array;
            }

            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (consumeIf(']')) {
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < input.length()) {
                char current = input.charAt(position++);
                if (current == '"') {
                    return value.toString();
                }
                if (current < 0x20) {
                    throw error("Control character in string");
                }
                if (current != '\\') {
                    value.append(current);
                    continue;
                }

                if (position >= input.length()) {
                    throw error("Incomplete escape");
                }
                char escaped = input.charAt(position++);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        value.append(escaped);
                        break;
                    case 'b':
                        value.append('\b');
                        break;
                    case 'f':
                        value.append('\f');
                        break;
                    case 'n':
                        value.append('\n');
                        break;
                    case 'r':
                        value.append('\r');
                        break;
                    case 't':
                        value.append('\t');
                        break;
                    case 'u':
                        value.append(parseUnicodeEscape());
                        break;
                    default:
                        throw error("Invalid escape");
                }
            }
            throw error("Unterminated string");
        }

        private char parseUnicodeEscape() {
            if (position + 4 > input.length()) {
                throw error("Incomplete unicode escape");
            }
            int codePoint = 0;
            for (int index = 0; index < 4; index += 1) {
                int digit = Character.digit(input.charAt(position++), 16);
                if (digit < 0) {
                    throw error("Invalid unicode escape");
                }
                codePoint = (codePoint << 4) | digit;
            }
            return (char) codePoint;
        }

        private NumberValue parseNumber() {
            int start = position;
            consumeIf('-');
            if (consumeIf('0')) {
                // A zero may not be followed by another integer digit.
                if (position < input.length() && isDigit(input.charAt(position))) {
                    throw error("Leading zero");
                }
            } else {
                if (position >= input.length() || !isNonZeroDigit(input.charAt(position))) {
                    throw error("Invalid number");
                }
                while (position < input.length() && isDigit(input.charAt(position))) {
                    position += 1;
                }
            }

            if (consumeIf('.')) {
                if (position >= input.length() || !isDigit(input.charAt(position))) {
                    throw error("Invalid fraction");
                }
                while (position < input.length() && isDigit(input.charAt(position))) {
                    position += 1;
                }
            }

            if (position < input.length()
                    && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
                position += 1;
                if (position < input.length()
                        && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                    position += 1;
                }
                if (position >= input.length() || !isDigit(input.charAt(position))) {
                    throw error("Invalid exponent");
                }
                while (position < input.length() && isDigit(input.charAt(position))) {
                    position += 1;
                }
            }
            return new NumberValue(input.substring(start, position));
        }

        private void consumeLiteral(String literal) {
            if (!input.startsWith(literal, position)) {
                throw error("Invalid literal");
            }
            position += literal.length();
        }

        private void skipWhitespace() {
            while (position < input.length()) {
                char current = input.charAt(position);
                if (current != ' ' && current != '\n' && current != '\r' && current != '\t') {
                    return;
                }
                position += 1;
            }
        }

        private void expect(char expected) {
            if (!consumeIf(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private boolean consumeIf(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position += 1;
                return true;
            }
            return false;
        }

        private boolean isDigit(char value) {
            return value >= '0' && value <= '9';
        }

        private boolean isNonZeroDigit(char value) {
            return value >= '1' && value <= '9';
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + position);
        }
    }

    private static final class Serializer {
        private Serializer() {}

        private static String write(Object value) {
            StringBuilder output = new StringBuilder();
            append(output, value);
            return output.toString();
        }

        private static void append(StringBuilder output, Object value) {
            if (value == null) {
                output.append("null");
            } else if (value instanceof String) {
                appendString(output, (String) value);
            } else if (value instanceof Boolean) {
                output.append(value);
            } else if (value instanceof NumberValue) {
                output.append(((NumberValue) value).raw);
            } else if (value instanceof List<?>) {
                appendList(output, (List<?>) value);
            } else if (value instanceof Map<?, ?>) {
                appendObject(output, (Map<?, ?>) value);
            } else {
                throw new IllegalArgumentException("Unsupported JSON value");
            }
        }

        private static void appendList(StringBuilder output, List<?> list) {
            output.append('[');
            for (int index = 0; index < list.size(); index += 1) {
                if (index > 0) {
                    output.append(',');
                }
                append(output, list.get(index));
            }
            output.append(']');
        }

        private static void appendObject(StringBuilder output, Map<?, ?> object) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                appendString(output, String.valueOf(entry.getKey()));
                output.append(':');
                append(output, entry.getValue());
            }
            output.append('}');
        }

        private static void appendString(StringBuilder output, String value) {
            output.append('"');
            for (int index = 0; index < value.length(); index += 1) {
                char current = value.charAt(index);
                switch (current) {
                    case '"':
                        output.append("\\\"");
                        break;
                    case '\\':
                        output.append("\\\\");
                        break;
                    case '\b':
                        output.append("\\b");
                        break;
                    case '\f':
                        output.append("\\f");
                        break;
                    case '\n':
                        output.append("\\n");
                        break;
                    case '\r':
                        output.append("\\r");
                        break;
                    case '\t':
                        output.append("\\t");
                        break;
                    default:
                        if (current < 0x20) {
                            output.append(String.format("\\u%04x", (int) current));
                        } else {
                            output.append(current);
                        }
                }
            }
            output.append('"');
        }
    }
}
