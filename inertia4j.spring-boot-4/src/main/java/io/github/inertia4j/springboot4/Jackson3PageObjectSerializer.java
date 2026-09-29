package io.github.inertia4j.springboot4;

import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.spi.OncePropMetadata;
import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.ScrollPropMetadata;
import io.github.inertia4j.spi.SerializationException;
import org.jspecify.annotations.NullMarked;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.annotation.JsonNaming;
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

    private final ObjectMapper objectMapper;

    /**
     * Constructs a serializer keeping the Java property names of objects inside props ({@link PropertyNaming#Camel}).
     */
    Jackson3PageObjectSerializer() {
        this(PropertyNaming.Camel);
    }

    /**
     * Constructs a serializer applying a naming strategy to the properties of objects inside props.
     * The keys of the page object itself, of its metadata and the keys of maps are never renamed.
     *
     * @param propertyNaming naming strategy, matching the one used to convert typed props.
     */
    Jackson3PageObjectSerializer(PropertyNaming propertyNaming) {
        JsonMapper.Builder builder = JsonMapper.builder()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false)
            .addMixIn(PageObject.class, ProtocolKeys.class)
            .addMixIn(ScrollPropMetadata.class, ProtocolKeys.class)
            .addMixIn(OncePropMetadata.class, ProtocolKeys.class);

        if (propertyNaming == PropertyNaming.Snake) {
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        }

        this.objectMapper = builder.build();
    }

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

    /**
     * Keeps the Inertia protocol keys as-is whatever the global naming strategy.
     */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    private abstract static class ProtocolKeys {}

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
