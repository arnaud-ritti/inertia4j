import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.arkoder.inertia4j.core.Property;
import dev.arkoder.inertia4j.core.PropertyIntrospector;
import dev.arkoder.inertia4j.core.PropertyNaming;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertyIntrospectorTest {
    private record Album(String title, int year, List<String> tags) {}

    private record Annotated(
        @JsonProperty("full_name") String name,
        @JsonIgnore String secret,
        @Nullable String nickname,
        String firstName
    ) {}

    public static class Base {
        public long getId() { return 1; }
    }

    public static class Child extends Base {
        private String name = "child";
        private boolean active = true;

        public String getName() { return name; }
        public boolean isActive() { return active; }
        public String getComputed() { return "computed"; }
        public static String getStatic() { return "static"; }
    }

    @Test
    void record_listsComponentsInDeclarationOrder() {
        assertEquals(List.of("title", "year", "tags"), names(Album.class, PropertyNaming.Camel));
    }

    @Test
    void record_exposesGenericTypeAndAccessor() {
        Property tags = PropertyIntrospector.properties(Album.class, PropertyNaming.Camel).get(2);

        assertTrue(tags.getGenericType() instanceof ParameterizedType);
        assertEquals("tags", tags.getAccessorName());
    }

    @Test
    void bean_listsSuperclassFieldsFirstThenGetterOnlyPropertiesByName() {
        assertEquals(List.of("name", "active", "computed", "id"), names(Child.class, PropertyNaming.Camel));
    }

    @Test
    void jsonProperty_renamesAndJsonIgnore_skips() {
        assertEquals(List.of("full_name", "nickname", "firstName"), names(Annotated.class, PropertyNaming.Camel));
    }

    @Test
    void snakeNaming_appliesOnlyToUnannotatedProperties() {
        assertEquals(List.of("full_name", "nickname", "first_name"), names(Annotated.class, PropertyNaming.Snake));
    }

    @Test
    void typeUseNullable_isCollected() {
        List<Property> properties = PropertyIntrospector.properties(Annotated.class, PropertyNaming.Camel);

        assertTrue(properties.get(1).hasAnnotation("Nullable"));
        assertFalse(properties.get(2).hasAnnotation("Nullable"));
    }

    @Test
    void read_invokesAccessorOfPrivateRecord() {
        Property title = PropertyIntrospector.properties(Album.class, PropertyNaming.Camel).get(0);

        assertEquals("Kind of Blue", title.read(new Album("Kind of Blue", 1959, List.of())));
    }

    private static List<String> names(Class<?> type, PropertyNaming naming) {
        return PropertyIntrospector.properties(type, naming).stream()
            .map(Property::getName)
            .collect(Collectors.toList());
    }
}
