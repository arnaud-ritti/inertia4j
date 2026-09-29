package io.github.inertia4j.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.SerializationException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * {@link PageObjectSerializer} implementation using Jackson for JSON serialization.
 */
@NullMarked
public class JacksonPageObjectSerializer implements PageObjectSerializer {
    private static final List<String> OptionalFields = List.of(
        "mergeProps",
        "prependProps",
        "deepMergeProps",
        "matchPropsOn",
        "deferredProps"
    );

    /**
     * The Jackson ObjectMapper instance used for serialization.
     * Configured to order map entries by keys for consistent output.
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /**
     * {@inheritDoc}
     * <p>
     * If {@code partialDataProps} is provided, only the properties specified
     * in the list will be included under the "props" key in the resulting JSON.
     */
    @Override
    public String serialize(
        PageObject pageObject,
        @Nullable List<String> partialDataProps
    ) throws SerializationException {
        try {
            ObjectNode tree = objectMapper.valueToTree(pageObject);
            removeNullOptionalFields(tree);
            if (partialDataProps != null) {
                ObjectNode propsNode = (ObjectNode) tree.get("props");
                propsNode.retain(partialDataProps);
            }
            return objectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            throw new SerializationException(e);
        }
    }

    private static void removeNullOptionalFields(ObjectNode tree) {
        for (String field : OptionalFields) {
            JsonNode node = tree.get(field);
            if (node != null && node.isNull()) {
                tree.remove(field);
            }
        }
    }
}
