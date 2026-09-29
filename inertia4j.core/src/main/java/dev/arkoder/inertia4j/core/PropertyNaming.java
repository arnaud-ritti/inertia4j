package dev.arkoder.inertia4j.core;

/**
 * Naming strategy applied to property names that have no explicit {@code @JsonProperty} name.
 * Must match the strategy configured on the JSON serializer.
 */
public enum PropertyNaming {
    /**
     * Keeps Java property names, e.g. {@code firstName}.
     */
    Camel,
    /**
     * Converts to snake case like Jackson's {@code SNAKE_CASE}, e.g. {@code first_name}.
     */
    Snake;

    /**
     * Applies this strategy to a Java property name.
     *
     * @param propertyName Java property name.
     * @return serialized property name.
     */
    public String apply(String propertyName) {
        if (this == Camel) {
            return propertyName;
        }

        return toSnakeCase(propertyName);
    }

    private static String toSnakeCase(String propertyName) {
        StringBuilder result = new StringBuilder(propertyName.length() + 4);
        boolean previousUpper = false;

        for (int i = 0; i < propertyName.length(); i++) {
            char c = propertyName.charAt(i);
            boolean upper = Character.isUpperCase(c);

            if (upper && !previousUpper && result.length() > 0 && result.charAt(result.length() - 1) != '_') {
                result.append('_');
            }

            result.append(Character.toLowerCase(c));
            previousUpper = upper;
        }

        return result.toString();
    }
}
