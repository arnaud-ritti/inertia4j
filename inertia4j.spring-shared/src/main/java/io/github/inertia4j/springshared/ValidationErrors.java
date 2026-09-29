package io.github.inertia4j.springshared;

import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Spring validation results to the shape of the Inertia {@code errors} prop.
 * Global errors, not bound to a field, are keyed by object name.
 */
public final class ValidationErrors {
    private ValidationErrors() {}

    /**
     * Gets the first error message of each field, the default shape of the {@code errors} prop.
     *
     * @param errors validation result.
     * @return first message by field.
     */
    public static Map<String, String> firstMessages(Errors errors) {
        Map<String, String> messages = new LinkedHashMap<>();
        allMessages(errors).forEach((field, fieldMessages) -> messages.put(field, fieldMessages.get(0)));

        return messages;
    }

    /**
     * Gets every error message of each field.
     *
     * @param errors validation result.
     * @return messages by field.
     */
    public static Map<String, List<String>> allMessages(Errors errors) {
        Map<String, List<String>> messages = new LinkedHashMap<>();

        for (ObjectError error : errors.getAllErrors()) {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : error.getCode();
            messages.computeIfAbsent(field, key -> new ArrayList<>()).add(message);
        }

        return messages;
    }
}
