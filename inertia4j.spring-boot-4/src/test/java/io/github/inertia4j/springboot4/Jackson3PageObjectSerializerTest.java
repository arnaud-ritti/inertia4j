package io.github.inertia4j.springboot4;

import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.spi.PageObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Jackson3PageObjectSerializerTest {
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
        return new PageObject(
            "Owners/Show",
            props,
            "/owners/1",
            true,
            true,
            "1",
            List.of("mergeMe"),
            List.of("prependMe"),
            List.of("deepMergeMe"),
            List.of("mergeMe.itemId"),
            Map.of("default", List.of("lateProp"))
        );
    }

    @Test
    void serialize_withCamelNaming_keepsNestedCamelKeys() throws Exception {
        String json = new Jackson3PageObjectSerializer()
            .serialize(pageObject(Map.of("owner", new Owner("Miles", new Address("Main")))), null);

        assertEquals(
            "{\"component\":\"Owners/Show\",\"props\":{\"owner\":{\"firstName\":\"Miles\",\"homeAddress\":{\"streetName\":\"Main\"}}},"
                + "\"url\":\"/owners/1\",\"version\":\"1\",\"encryptHistory\":true,\"clearHistory\":true,"
                + "\"mergeProps\":[\"mergeMe\"],\"prependProps\":[\"prependMe\"],\"deepMergeProps\":[\"deepMergeMe\"],"
                + "\"matchPropsOn\":[\"mergeMe.itemId\"],\"deferredProps\":{\"default\":[\"lateProp\"]}}",
            json
        );
    }

    @Test
    void serialize_withSnakeNaming_snakeCasesUserObjectsButKeepsProtocolKeys() throws Exception {
        String json = new Jackson3PageObjectSerializer(PropertyNaming.Snake)
            .serialize(pageObject(Map.of("owner", new Owner("Miles", new Address("Main")))), null);

        assertEquals(
            "{\"component\":\"Owners/Show\",\"props\":{\"owner\":{\"first_name\":\"Miles\",\"home_address\":{\"street_name\":\"Main\"}}},"
                + "\"url\":\"/owners/1\",\"version\":\"1\",\"encryptHistory\":true,\"clearHistory\":true,"
                + "\"mergeProps\":[\"mergeMe\"],\"prependProps\":[\"prependMe\"],\"deepMergeProps\":[\"deepMergeMe\"],"
                + "\"matchPropsOn\":[\"mergeMe.itemId\"],\"deferredProps\":{\"default\":[\"lateProp\"]}}",
            json
        );
    }

    @Test
    void serialize_withSnakeNaming_keepsPropsMapKeys() throws Exception {
        String json = new Jackson3PageObjectSerializer(PropertyNaming.Snake)
            .serialize(pageObject(Map.of("rawKey", Map.of("innerKey", new Pet("Rex")))), null);

        assertEquals("{\"rawKey\":{\"innerKey\":{\"pet_name\":\"Rex\"}}}", propsOf(json));
    }

    private static String propsOf(String json) {
        int start = json.indexOf("\"props\":") + "\"props\":".length();
        int end = json.indexOf(",\"url\":");

        return json.substring(start, end);
    }
}
