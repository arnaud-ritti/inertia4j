import io.github.inertia4j.core.HttpResponse;
import io.github.inertia4j.core.Precognition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrecognitionTest {
    @Test
    void isPrecognitive_detectsPrecognitionHeader() {
        assertTrue(Precognition.isPrecognitive(new FakeHttpRequest("POST", Map.of("Precognition", "true"))));
        assertFalse(Precognition.isPrecognitive(new FakeHttpRequest("POST", Map.of())));
    }

    @Test
    void respond_withoutErrors_returns204WithSuccessHeaders() {
        HttpResponse response = Precognition.respond(new FakeHttpRequest("POST", Map.of("Precognition", "true")), Map.of());

        assertEquals(204, response.getCode());
        assertEquals(List.of("true"), response.getHeaders().get("Precognition"));
        assertEquals(List.of("true"), response.getHeaders().get("Precognition-Success"));
        assertEquals(List.of("Precognition"), response.getHeaders().get("Vary"));
        assertNull(response.getBody());
    }

    @Test
    void respond_withErrors_returns422WithErrors() {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        errors.put("name", List.of("The name field is required."));
        errors.put("email", List.of("The email must be valid.", "The email is taken."));

        HttpResponse response = Precognition.respond(new FakeHttpRequest("POST", Map.of("Precognition", "true")), errors);

        assertEquals(422, response.getCode());
        assertEquals(List.of("true"), response.getHeaders().get("Precognition"));
        assertFalse(response.getHeaders().containsKey("Precognition-Success"));
        assertEquals(
            "{\"message\":\"The name field is required. (and 2 more errors)\",\"errors\":{\"name\":[\"The name field is required.\"],\"email\":[\"The email must be valid.\",\"The email is taken.\"]}}",
            response.getBody()
        );
    }

    @Test
    void respond_withValidateOnly_ignoresErrorsOfOtherFields() {
        var request = new FakeHttpRequest("POST", Map.of("Precognition", "true", "Precognition-Validate-Only", "email,items"));
        Map<String, List<String>> errors = new LinkedHashMap<>();
        errors.put("name", List.of("The name field is required."));
        errors.put("items.0.name", List.of("The \"item\" name is required."));

        HttpResponse response = Precognition.respond(request, errors);

        assertEquals(422, response.getCode());
        assertEquals(
            "{\"message\":\"The \\\"item\\\" name is required.\",\"errors\":{\"items.0.name\":[\"The \\\"item\\\" name is required.\"]}}",
            response.getBody()
        );
    }

    @Test
    void respond_withValidateOnly_whenOnlyOtherFieldsFail_returns204() {
        var request = new FakeHttpRequest("POST", Map.of("Precognition", "true", "Precognition-Validate-Only", "email"));

        HttpResponse response = Precognition.respond(request, Map.of("name", List.of("Required.")));

        assertEquals(204, response.getCode());
    }
}
