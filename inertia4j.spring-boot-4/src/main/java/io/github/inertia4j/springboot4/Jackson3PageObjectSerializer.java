package io.github.inertia4j.springboot4;

import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.SerializationException;
import org.jspecify.annotations.NullMarked;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@link PageObjectSerializer} implementation using Jackson 3 for JSON serialization.
 */
@NullMarked
class Jackson3PageObjectSerializer implements PageObjectSerializer {
    private static final Set<String> RequiredFields = Set.of("component", "props", "url", "version");

    private final ObjectMapper objectMapper = JsonMapper.builder()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false)
        .build();

    @Override
    public String serialize(PageObject pageObject) throws SerializationException {
        try {
            ObjectNode tree = objectMapper.valueToTree(pageObject);
            removeEmptyMetadata(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new SerializationException(e);
        }
    }

    private static void removeEmptyMetadata(ObjectNode tree) {
        List<String> fields = new ArrayList<>(tree.propertyNames());

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

        return node.isContainer() && node.isEmpty();
    }
}
