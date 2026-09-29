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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@link PageObjectSerializer} implementation using Jackson for JSON serialization.
 */
@NullMarked
public class JacksonPageObjectSerializer implements PageObjectSerializer {
    private static final Set<String> RequiredFields = Set.of("component", "props", "url", "version");

    /**
     * The Jackson ObjectMapper instance used for serialization.
     * Configured to order map entries by keys for consistent output.
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /**
     * {@inheritDoc}
     */
    @Override
    public String serialize(PageObject pageObject) throws SerializationException {
        try {
            ObjectNode tree = objectMapper.valueToTree(pageObject);
            removeEmptyMetadata(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new SerializationException(e);
        }
    }

    private static void removeEmptyMetadata(ObjectNode tree) {
        List<String> fields = new ArrayList<>();
        tree.fieldNames().forEachRemaining(fields::add);

        for (String field : fields) {
            if (!RequiredFields.contains(field) && isEmpty(tree.get(field))) {
                tree.remove(field);
            }
        }
    }

    private static boolean isEmpty(JsonNode node) {
        if (node.isNull()) {
            return true;
        }

        if (node.isBoolean()) {
            return !node.booleanValue();
        }

        return node.isContainerNode() && node.isEmpty();
    }
}
