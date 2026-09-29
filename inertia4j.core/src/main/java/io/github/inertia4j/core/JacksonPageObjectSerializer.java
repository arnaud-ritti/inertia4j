package io.github.inertia4j.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
    private final ObjectMapper objectMapper;

    /**
     * Constructs a serializer keeping the Java property names of objects inside props ({@link PropertyNaming#Camel}).
     */
    public JacksonPageObjectSerializer() {
        this(PropertyNaming.Camel);
    }

    /**
     * Constructs a serializer applying a naming strategy to the properties of objects inside props.
     * The keys of the page object itself ({@code component}, {@code props}, {@code encryptHistory}, …) and the keys of
     * maps are never renamed.
     *
     * @param propertyNaming naming strategy, matching the one used to convert typed props.
     */
    public JacksonPageObjectSerializer(PropertyNaming propertyNaming) {
        ObjectMapper mapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .addMixIn(PageObject.class, ProtocolKeys.class);

        if (propertyNaming == PropertyNaming.Snake) {
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        }

        this.objectMapper = mapper;
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
        } catch (JsonProcessingException e) {
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
