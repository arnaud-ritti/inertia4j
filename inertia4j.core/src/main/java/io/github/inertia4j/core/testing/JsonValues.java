package io.github.inertia4j.core.testing;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Navigates and compares parsed JSON values: maps, lists, strings, numbers, booleans and {@code null}.
 */
@NullMarked
final class JsonValues {
    /**
     * Returned by {@link #get(Object, String)} when the path does not exist, to tell it apart from a {@code null} value.
     */
    static final Object Missing = new Object() {
        @Override
        public String toString() {
            return "<missing>";
        }
    };

    private JsonValues() {}

    /**
     * Gets the value at a dotted path, where numeric segments index lists.
     *
     * @param root value the path is relative to.
     * @param path dotted path, or an empty string for the root itself.
     * @return the value, possibly {@code null}, or {@link #Missing}.
     */
    static @Nullable Object get(@Nullable Object root, String path) {
        if (path.isEmpty()) {
            return root;
        }

        @Nullable Object current = root;

        for (String segment : path.split("\\.", -1)) {
            if (current instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) current;

                if (!map.containsKey(segment)) {
                    return Missing;
                }

                current = map.get(segment);
                continue;
            }

            if (current instanceof List) {
                List<?> list = (List<?>) current;
                int index = parseIndex(segment);

                if (index < 0 || index >= list.size()) {
                    return Missing;
                }

                current = list.get(index);
                continue;
            }

            return Missing;
        }

        return current;
    }

    private static int parseIndex(String segment) {
        if (segment.isEmpty() || segment.length() > 9) {
            return -1;
        }

        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return -1;
            }
        }

        return Integer.parseInt(segment);
    }

    /**
     * Compares an expected value with a parsed JSON value. Numbers are compared by value whatever their type,
     * enums by name, characters as strings, maps and collections (or arrays) recursively.
     */
    static boolean matches(@Nullable Object expected, @Nullable Object actual) {
        if (expected == null || actual == null) {
            return expected == null && actual == null;
        }

        if (expected instanceof Number && actual instanceof Number) {
            return numbersEqual((Number) expected, (Number) actual);
        }

        if (expected instanceof Enum) {
            return ((Enum<?>) expected).name().equals(actual);
        }

        if (expected instanceof Character) {
            return expected.toString().equals(actual);
        }

        if (expected instanceof Map) {
            return mapsMatch((Map<?, ?>) expected, actual);
        }

        List<?> expectedElements = asList(expected);

        if (expectedElements != null) {
            return listsMatch(expectedElements, actual);
        }

        return expected.equals(actual);
    }

    private static boolean mapsMatch(Map<?, ?> expected, Object actual) {
        if (!(actual instanceof Map)) {
            return false;
        }

        Map<?, ?> actualMap = (Map<?, ?>) actual;

        if (expected.size() != actualMap.size()) {
            return false;
        }

        for (Map.Entry<?, ?> entry : expected.entrySet()) {
            String key = String.valueOf(entry.getKey());

            if (!actualMap.containsKey(key)) {
                return false;
            }

            if (!matches(entry.getValue(), actualMap.get(key))) {
                return false;
            }
        }

        return true;
    }

    private static boolean listsMatch(List<?> expected, Object actual) {
        if (!(actual instanceof List)) {
            return false;
        }

        List<?> actualList = (List<?>) actual;

        if (expected.size() != actualList.size()) {
            return false;
        }

        for (int i = 0; i < expected.size(); i++) {
            if (!matches(expected.get(i), actualList.get(i))) {
                return false;
            }
        }

        return true;
    }

    static @Nullable List<?> asList(Object value) {
        if (value instanceof List) {
            return (List<?>) value;
        }

        if (value instanceof Collection) {
            return new ArrayList<>((Collection<?>) value);
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<@Nullable Object> elements = new ArrayList<>(length);

            for (int i = 0; i < length; i++) {
                elements.add(Array.get(value, i));
            }

            return elements;
        }

        return null;
    }

    private static boolean numbersEqual(Number expected, Number actual) {
        BigDecimal expectedDecimal = toBigDecimal(expected);
        BigDecimal actualDecimal = toBigDecimal(actual);

        if (expectedDecimal == null || actualDecimal == null) {
            return Objects.equals(expected.doubleValue(), actual.doubleValue());
        }

        return expectedDecimal.compareTo(actualDecimal) == 0;
    }

    private static @Nullable BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }

        if (number instanceof BigInteger) {
            return new BigDecimal((BigInteger) number);
        }

        if (number instanceof Double || number instanceof Float) {
            double value = number.doubleValue();

            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }

            return new BigDecimal(number.toString());
        }

        return BigDecimal.valueOf(number.longValue());
    }

    /**
     * Counts the elements of a list or the entries of a map.
     *
     * @return the size, or {@code -1} if the value is neither.
     */
    static int size(@Nullable Object value) {
        if (value instanceof List) {
            return ((List<?>) value).size();
        }

        if (value instanceof Map) {
            return ((Map<?, ?>) value).size();
        }

        return -1;
    }

    /**
     * Renders a value for failure messages, as JSON-like text.
     */
    static String describe(@Nullable Object value) {
        StringBuilder builder = new StringBuilder();
        describe(value, builder);

        return builder.toString();
    }

    private static void describe(@Nullable Object value, StringBuilder builder) {
        if (value == null) {
            builder.append("null");
            return;
        }

        if (value instanceof CharSequence || value instanceof Character || value instanceof Enum) {
            builder.append('"').append(value instanceof Enum ? ((Enum<?>) value).name() : value).append('"');
            return;
        }

        if (value instanceof Map) {
            builder.append('{');
            Iterator<? extends Map.Entry<?, ?>> entries = ((Map<?, ?>) value).entrySet().iterator();

            while (entries.hasNext()) {
                Map.Entry<?, ?> entry = entries.next();
                builder.append('"').append(entry.getKey()).append("\":");
                describe(entry.getValue(), builder);

                if (entries.hasNext()) {
                    builder.append(',');
                }
            }

            builder.append('}');
            return;
        }

        List<?> elements = asList(value);

        if (elements != null) {
            builder.append('[');

            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }

                describe(elements.get(i), builder);
            }

            builder.append(']');
            return;
        }

        builder.append(value);
    }

    /**
     * Names the JSON type of a value for failure messages.
     */
    static String typeOf(@Nullable Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Map) {
            return "object";
        }

        if (value instanceof List) {
            return "array";
        }

        if (value instanceof CharSequence) {
            return "string";
        }

        if (value instanceof Number) {
            return "number";
        }

        if (value instanceof Boolean) {
            return "boolean";
        }

        return value.getClass().getSimpleName();
    }
}
