package io.github.inertia4j.springboot4;

import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.SerializationException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * {@link PageObjectSerializer} implementation using Jackson 3 for JSON serialization.
 */
@NullMarked
class Jackson3PageObjectSerializer implements PageObjectSerializer {
    private static final List<String> OptionalFields = List.of(
        "mergeProps",
        "prependProps",
        "deepMergeProps",
        "matchPropsOn",
        "deferredProps"
    );

    private final ObjectMapper objectMapper;

    /**
     * Constructs a serializer keeping the Java property names of objects inside props ({@link PropertyNaming#Camel}).
     */
    Jackson3PageObjectSerializer() {
        this(PropertyNaming.Camel);
    }

    /**
     * Constructs a serializer applying a naming strategy to the properties of objects inside props.
     * The keys of the page object itself and the keys of maps are never renamed.
     *
     * @param propertyNaming naming strategy, matching the one used to convert typed props.
     */
    Jackson3PageObjectSerializer(PropertyNaming propertyNaming) {
        JsonMapper.Builder builder = JsonMapper.builder()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false)
            .addMixIn(PageObject.class, ProtocolKeys.class);

        if (propertyNaming == PropertyNaming.Snake) {
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        }

        this.objectMapper = builder.build();
    }

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
        } catch (JacksonException e) {
            throw new SerializationException(e);
        }
    }

    /**
     * Keeps the Inertia protocol keys of the page object as-is whatever the global naming strategy.
     */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    private abstract static class ProtocolKeys {}

    private static void removeNullOptionalFields(ObjectNode tree) {
        for (String field : OptionalFields) {
            JsonNode node = tree.get(field);
            if (node != null && node.isNull()) {
                tree.remove(field);
            }
        }
    }
}
