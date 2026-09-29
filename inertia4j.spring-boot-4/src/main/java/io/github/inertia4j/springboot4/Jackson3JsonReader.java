package io.github.inertia4j.springboot4;

import io.github.inertia4j.spi.JsonReader;
import io.github.inertia4j.spi.SerializationException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * {@link JsonReader} implementation using Jackson 3.
 */
@NullMarked
class Jackson3JsonReader implements JsonReader {
    private static final TypeReference<Map<String, @Nullable Object>> MapType = new TypeReference<>() {};

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Override
    public Map<String, @Nullable Object> readObject(String json) throws SerializationException {
        try {
            Map<String, @Nullable Object> object = objectMapper.readValue(json, MapType);

            if (object == null) {
                throw new SerializationException("Expected a JSON object, got null");
            }

            return object;
        } catch (JacksonException e) {
            throw new SerializationException(e);
        }
    }
}
