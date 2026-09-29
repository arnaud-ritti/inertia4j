import io.github.inertia4j.annotations.InertiaForm;
import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.annotations.InertiaShared;
import io.github.inertia4j.annotations.TypeScriptName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationsTest {
    @InertiaPage("Users/Show")
    @TypeScriptName("UserPage")
    static class PageProps {}

    @InertiaShared
    static class SharedProps {}

    @InertiaForm
    static class FormProps {}

    @Test
    void annotations_areVisibleAtRuntime() {
        assertEquals("Users/Show", PageProps.class.getAnnotation(InertiaPage.class).value());
        assertEquals("UserPage", PageProps.class.getAnnotation(TypeScriptName.class).value());
        assertTrue(SharedProps.class.isAnnotationPresent(InertiaShared.class));
        assertTrue(FormProps.class.isAnnotationPresent(InertiaForm.class));
    }
}
