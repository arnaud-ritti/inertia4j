import dev.arkoder.inertia4j.core.SimpleTemplateRenderer;
import dev.arkoder.inertia4j.core.TemplateRenderingException;
import dev.arkoder.inertia4j.spi.RenderedPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleTemplateRendererTest {
    @Test
    void render_replacesHeadAndAppPlaceholders() {
        String html = new SimpleTemplateRenderer("template.html")
            .render(new RenderedPage("<title>$1 \\ Title</title>", "<div id=\"app\">$0</div>"));

        assertEquals(
            "<!doctype html>\n<html lang=\"en\">\n  <head><title>$1 \\ Title</title></head>\n  <body>\n    <div id=\"app\">$0</div>\n  </body>\n</html>\n",
            html
        );
    }

    @Test
    void constructor_whenTemplateUsesLegacyPlaceholder_throwsWithMigrationHint() {
        TemplateRenderingException exception = assertThrows(
            TemplateRenderingException.class,
            () -> new SimpleTemplateRenderer("legacy-template.html")
        );

        assertTrue(exception.getMessage().contains("@InertiaApp@"));
    }

    @Test
    void constructor_whenTemplateIsMissing_throws() {
        assertThrows(TemplateRenderingException.class, () -> new SimpleTemplateRenderer("missing.html"));
    }
}
