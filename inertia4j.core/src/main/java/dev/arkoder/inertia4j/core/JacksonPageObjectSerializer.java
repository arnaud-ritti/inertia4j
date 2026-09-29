package dev.arkoder.inertia4j.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.arkoder.inertia4j.spi.OncePropMetadata;
import dev.arkoder.inertia4j.spi.PageObject;
import dev.arkoder.inertia4j.spi.PageObjectSerializer;
import dev.arkoder.inertia4j.spi.ScrollPropMetadata;
import dev.arkoder.inertia4j.spi.SerializationException;
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
    private final ObjectMapper objectMapper;

    /**
     * Constructs a serializer keeping the Java property names of objects inside props ({@link PropertyNaming#Camel}).
     */
    public JacksonPageObjectSerializer() {
        this(PropertyNaming.Camel);
    }

    /**
     * Constructs a serializer applying a naming strategy to the properties of objects inside props.
     * The keys of the page object itself ({@code component}, {@code props}, {@code encryptHistory}, …), of its
     * metadata and the keys of maps are never renamed.
     *
     * @param propertyNaming naming strategy, matching the one used to convert typed props.
     */
    public JacksonPageObjectSerializer(PropertyNaming propertyNaming) {
        ObjectMapper mapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .addMixIn(PageObject.class, ProtocolKeys.class)
            .addMixIn(ScrollPropMetadata.class, ProtocolKeys.class)
            .addMixIn(OncePropMetadata.class, ProtocolKeys.class);

        if (propertyNaming == PropertyNaming.Snake) {
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        }

        this.objectMapper = mapper;
    }

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

    /**
     * Keeps the Inertia protocol keys as-is whatever the global naming strategy.
     */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    private abstract static class ProtocolKeys {}

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
