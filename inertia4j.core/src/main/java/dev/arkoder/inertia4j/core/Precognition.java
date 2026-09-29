package dev.arkoder.inertia4j.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helpers for Precognition validation requests, sent by Inertia forms to validate fields ahead of submission.
 * A precognitive request must be validated but not executed: respond with {@link #respond(HttpRequest, Map)}
 * right after validation.
 *
 * @see <a href="https://inertiajs.com/docs/v3/the-basics/forms#precognition">Inertia Precognition</a>
 */
public final class Precognition {
    private Precognition() {}

    /**
     * Checks whether the request is a Precognition validation request.
     *
     * @param request incoming request.
     * @return {@code true} for Precognition requests.
     */
    public static boolean isPrecognitive(HttpRequest request) {
        return "true".equalsIgnoreCase(request.getHeader(InertiaHeaders.Precognition));
    }

    /**
     * Gets the fields the client asks to validate.
     *
     * @param request incoming request.
     * @return fields to validate, or an empty list to validate every field.
     */
    public static List<String> validateOnly(HttpRequest request) {
        return PropsResolver.parseHeaderList(request.getHeader(InertiaHeaders.PrecognitionValidateOnly));
    }

    /**
     * Checks whether a field must be validated for this request: every field is validated unless the client
     * lists the fields to validate. Nested fields, e.g. {@code items.0.name}, are validated with their parent.
     *
     * @param request incoming request.
     * @param field field name.
     * @return {@code true} if the field must be validated.
     */
    public static boolean shouldValidate(HttpRequest request, String field) {
        List<String> validateOnly = validateOnly(request);

        return validateOnly.isEmpty() || validateOnly.stream().anyMatch(selected -> isSameOrNested(field, selected));
    }

    /**
     * Creates the response to a Precognition request: {@code 204 No Content} with {@code Precognition-Success: true}
     * when there are no errors on the validated fields, or {@code 422 Unprocessable Entity} with the errors otherwise.
     * Errors on fields the client did not ask to validate are left out.
     *
     * @param request incoming request.
     * @param errors validation error messages, by field.
     * @return the Precognition response.
     */
    public static HttpResponse respond(HttpRequest request, Map<String, List<String>> errors) {
        Map<String, List<String>> validatedErrors = errors.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .filter(entry -> shouldValidate(request, entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        HttpResponse response = new HttpResponse()
            .setHeader(InertiaHeaders.Precognition, "true")
            .setHeader("Vary", InertiaHeaders.Precognition);

        if (validatedErrors.isEmpty()) {
            return response
                .setCode(204)
                .setHeader(InertiaHeaders.PrecognitionSuccess, "true");
        }

        return response
            .setCode(422)
            .setHeader("Content-Type", "application/json")
            .setBody(errorsJson(validatedErrors));
    }

    private static boolean isSameOrNested(String field, String selected) {
        return field.equals(selected) || field.startsWith(selected + ".");
    }

    private static String errorsJson(Map<String, List<String>> errors) {
        String errorsObject = errors.entrySet().stream()
            .map(entry -> JsonStrings.quote(entry.getKey()) + ":" + entry.getValue().stream()
                .map(JsonStrings::quote)
                .collect(Collectors.joining(",", "[", "]")))
            .collect(Collectors.joining(",", "{", "}"));

        return "{\"message\":" + JsonStrings.quote(summary(errors)) + ",\"errors\":" + errorsObject + "}";
    }

    private static String summary(Map<String, List<String>> errors) {
        List<String> messages = errors.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());

        int remaining = messages.size() - 1;
        if (remaining == 0) {
            return messages.get(0);
        }

        return messages.get(0) + " (and " + remaining + " more error" + (remaining == 1 ? "" : "s") + ")";
    }
}
