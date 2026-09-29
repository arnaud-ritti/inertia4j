import io.github.inertia4j.core.PropertyNaming;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertyNamingTest {
    @Test
    void camel_keepsName() {
        assertEquals("firstName", PropertyNaming.Camel.apply("firstName"));
    }

    @Test
    void snake_matchesJacksonSnakeCase() {
        assertEquals("first_name", PropertyNaming.Snake.apply("firstName"));
        assertEquals("user_id", PropertyNaming.Snake.apply("userID"));
        assertEquals("urlvalue", PropertyNaming.Snake.apply("URLValue"));
        assertEquals("name", PropertyNaming.Snake.apply("name"));
    }
}
