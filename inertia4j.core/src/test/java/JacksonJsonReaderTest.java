import io.github.inertia4j.core.JacksonJsonReader;
import io.github.inertia4j.spi.SerializationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JacksonJsonReaderTest {
    private final JacksonJsonReader reader = new JacksonJsonReader();

    @Test
    void readObject_readsJsonObjects() throws SerializationException {
        assertEquals(Map.of("status", "OK"), reader.readObject("{\"status\":\"OK\"}"));
    }

    @Test
    void readObject_rejectsJsonNull() {
        assertThrows(SerializationException.class, () -> reader.readObject("null"));
    }
}
