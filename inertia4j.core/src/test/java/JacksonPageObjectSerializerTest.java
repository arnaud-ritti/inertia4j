import io.github.inertia4j.core.DefaultPageObjectSerializer;
import io.github.inertia4j.core.JacksonPageObjectSerializer;
import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.spi.OncePropMetadata;
import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.ScrollPropMetadata;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonPageObjectSerializerTest {
    public record Address(String streetName) {}

    public record Owner(String firstName, Address homeAddress) {}

    public static class Pet {
        private final String petName;

        public Pet(String petName) {
            this.petName = petName;
        }

        public String getPetName() {
            return petName;
        }
    }

    private static PageObject pageObject(Map<String, Object> props) {
        return PageObject.builder("Owners/Show", "/owners/1", "1")
            .props(new LinkedHashMap<>(props))
            .encryptHistory(true)
            .clearHistory(true)
            .mergeProps(List.of("mergeMe"))
            .prependProps(List.of("prependMe"))
            .deepMergeProps(List.of("deepMergeMe"))
            .matchPropsOn(List.of("mergeMe.itemId"))
            .scrollProps(Map.of("scrollMe", new ScrollPropMetadata("page", null, 2, 1, false)))
            .deferredProps(Map.of("default", List.of("lateProp")))
            .onceProps(Map.of("onceMe", new OncePropMetadata("onceMe", 10L)))
            .build();
    }

    @Test
    void serialize_withCamelNaming_keepsNestedCamelKeys() throws Exception {
        String json = new JacksonPageObjectSerializer()
            .serialize(pageObject(Map.of("owner", new Owner("Miles", new Address("Main")))));

        assertEquals(
            "{\"component\":\"Owners/Show\",\"props\":{\"owner\":{\"firstName\":\"Miles\",\"homeAddress\":{\"streetName\":\"Main\"}}},"
                + "\"url\":\"/owners/1\",\"version\":\"1\",\"encryptHistory\":true,\"clearHistory\":true,"
                + "\"mergeProps\":[\"mergeMe\"],\"prependProps\":[\"prependMe\"],\"deepMergeProps\":[\"deepMergeMe\"],"
                + "\"matchPropsOn\":[\"mergeMe.itemId\"],"
                + "\"scrollProps\":{\"scrollMe\":{\"pageName\":\"page\",\"previousPage\":null,\"nextPage\":2,\"currentPage\":1,\"reset\":false}},"
                + "\"deferredProps\":{\"default\":[\"lateProp\"]},"
                + "\"onceProps\":{\"onceMe\":{\"prop\":\"onceMe\",\"expiresAt\":10}}}",
            json
        );
    }

    @Test
    void serialize_withSnakeNaming_snakeCasesUserObjectsButKeepsProtocolKeys() throws Exception {
        String json = new JacksonPageObjectSerializer(PropertyNaming.Snake)
            .serialize(pageObject(Map.of("owner", new Owner("Miles", new Address("Main")))));

        assertEquals(
            "{\"component\":\"Owners/Show\",\"props\":{\"owner\":{\"first_name\":\"Miles\",\"home_address\":{\"street_name\":\"Main\"}}},"
                + "\"url\":\"/owners/1\",\"version\":\"1\",\"encryptHistory\":true,\"clearHistory\":true,"
                + "\"mergeProps\":[\"mergeMe\"],\"prependProps\":[\"prependMe\"],\"deepMergeProps\":[\"deepMergeMe\"],"
                + "\"matchPropsOn\":[\"mergeMe.itemId\"],"
                + "\"scrollProps\":{\"scrollMe\":{\"pageName\":\"page\",\"previousPage\":null,\"nextPage\":2,\"currentPage\":1,\"reset\":false}},"
                + "\"deferredProps\":{\"default\":[\"lateProp\"]},"
                + "\"onceProps\":{\"onceMe\":{\"prop\":\"onceMe\",\"expiresAt\":10}}}",
            json
        );
    }

    @Test
    void serialize_withSnakeNaming_keepsPropsMapKeys() throws Exception {
        String json = new JacksonPageObjectSerializer(PropertyNaming.Snake)
            .serialize(pageObject(Map.of("rawKey", Map.of("innerKey", new Pet("Rex")))));

        assertEquals("{\"rawKey\":{\"innerKey\":{\"pet_name\":\"Rex\"}}}", propsOf(json));
    }

    @Test
    void defaultSerializer_withSnakeNaming_snakeCasesUserObjects() throws Exception {
        PageObjectSerializer serializer = new DefaultPageObjectSerializer(PropertyNaming.Snake);

        String json = serializer.serialize(pageObject(Map.of("pet", new Pet("Rex"))));

        assertEquals("{\"pet\":{\"pet_name\":\"Rex\"}}", propsOf(json));
    }

    @Test
    void defaultSerializer_withoutNaming_keepsCamelKeys() throws Exception {
        String json = new DefaultPageObjectSerializer().serialize(pageObject(Map.of("pet", new Pet("Rex"))));

        assertEquals("{\"pet\":{\"petName\":\"Rex\"}}", propsOf(json));
    }

    private static String propsOf(String json) {
        int start = json.indexOf("\"props\":") + "\"props\":".length();
        int end = json.indexOf(",\"url\":");

        return json.substring(start, end);
    }
}
