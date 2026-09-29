package dev.arkoder.inertia4j.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.arkoder.inertia4j.spi.JsonReader;
import dev.arkoder.inertia4j.spi.SerializationException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * {@link JsonReader} implementation using Jackson.
 */
@NullMarked
public class JacksonJsonReader implements JsonReader {
    private static final TypeReference<Map<String, @Nullable Object>> MapType = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, @Nullable Object> readObject(String json) throws SerializationException {
        try {
            Map<String, @Nullable Object> object = objectMapper.readValue(json, MapType);

            if (object == null) {
                throw new SerializationException("Expected a JSON object, got null");
            }

            return object;
        } catch (JsonProcessingException e) {
            throw new SerializationException(e);
        }
    }
}
