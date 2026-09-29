import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.core.DeferredProp;
import io.github.inertia4j.core.InertiaProps;
import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.core.PropsExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropsExtractorTest {
    @InertiaPage("Albums/Index")
    private record AlbumsIndex(List<String> titles, String firstName, DeferredProp<Integer> total, String note) {}

    private record NotAPage(String name) {}

    private record PropertyInclusion(
        @JsonInclude(JsonInclude.Include.NON_NULL) String nonNull,
        @JsonInclude(JsonInclude.Include.NON_ABSENT) String nonAbsent,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) String nonEmpty,
        @JsonInclude(JsonInclude.Include.ALWAYS) String always,
        String plain
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ClassInclusion(String omitted, @JsonInclude(JsonInclude.Include.ALWAYS) String kept, String present) {}

    @Test
    void toMap_keepsDeclarationOrderAndNullValues() {
        Map<String, Object> props = PropsExtractor.toMap(new AlbumsIndex(List.of("A"), "Miles", null, null));

        assertEquals(List.of("titles", "firstName", "total", "note"), List.copyOf(props.keySet()));
        assertEquals(List.of("A"), props.get("titles"));
        assertNull(props.get("note"));
        assertTrue(props.containsKey("note"));
    }

    @Test
    void toMap_withPropertyJsonIncludeOmittingNulls_omitsNullValues() {
        Map<String, Object> props = PropsExtractor.toMap(new PropertyInclusion(null, null, null, null, null));

        assertEquals(List.of("always", "plain"), List.copyOf(props.keySet()));
    }

    @Test
    void toMap_withPropertyJsonIncludeOmittingNulls_keepsNonNullValues() {
        Map<String, Object> props = PropsExtractor.toMap(new PropertyInclusion("a", "b", "c", null, null));

        assertEquals(List.of("nonNull", "nonAbsent", "nonEmpty", "always", "plain"), List.copyOf(props.keySet()));
    }

    @Test
    void toMap_withClassJsonIncludeOmittingNulls_omitsNullValuesUnlessPropertyOverrides() {
        Map<String, Object> props = PropsExtractor.toMap(new ClassInclusion(null, null, "x"));

        assertFalse(props.containsKey("omitted"));
        assertTrue(props.containsKey("kept"));
        assertEquals("x", props.get("present"));
    }

    @Test
    void toMap_appliesNaming() {
        Map<String, Object> props = PropsExtractor.toMap(new AlbumsIndex(List.of(), "Miles", null, null), PropertyNaming.Snake);

        assertEquals("Miles", props.get("first_name"));
    }

    @Test
    void toMap_leavesInertiaPropsUntouched() {
        DeferredProp<Integer> total = InertiaProps.defer(() -> 3);

        Map<String, Object> props = PropsExtractor.toMap(new AlbumsIndex(List.of(), "Miles", total, null));

        assertSame(total, props.get("total"));
    }

    @Test
    void componentName_readsInertiaPage() {
        assertEquals("Albums/Index", PropsExtractor.componentName(new AlbumsIndex(List.of(), "", null, null)));
    }

    @Test
    void componentName_withoutInertiaPage_throws() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> PropsExtractor.componentName(new NotAPage("x"))
        );

        assertTrue(exception.getMessage().contains("NotAPage"));
        assertTrue(exception.getMessage().contains("@InertiaPage"));
    }
}
