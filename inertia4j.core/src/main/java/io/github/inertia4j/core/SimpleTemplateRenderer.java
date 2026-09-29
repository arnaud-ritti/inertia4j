package io.github.inertia4j.core;

import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.core.vite.ViteException;
import io.github.inertia4j.spi.RenderedPage;
import io.github.inertia4j.spi.TemplateRenderer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple {@link TemplateRenderer} implementation used by default if no specific renderer is provided.
 * It loads a template file from the classpath and replaces the {@value #HeadPlaceholder} placeholder with the
 * server-side rendered head elements, and the {@value #AppPlaceholder} placeholder with the page object script
 * element and the application root element.
 * When given a {@link Vite} instance, it also replaces the <code>@Vite(entry, ...)@</code> and
 * <code>@ViteReactRefresh@</code> placeholders with the tags loading the frontend.
 */
@NullMarked
public class SimpleTemplateRenderer implements TemplateRenderer {
    /**
     * Placeholder replaced by the elements belonging to the document head, empty unless server-side rendered.
     */
    public static final String HeadPlaceholder = "@InertiaHead@";

    /**
     * Placeholder replaced by the page object script element and the application root element.
     */
    public static final String AppPlaceholder = "@InertiaApp@";

    private static final String LegacyPlaceholder = "@PageObject@";
    private static final Pattern vitePattern = Pattern.compile("@Vite\\(([^)]*)\\)@");
    private static final String reactRefreshPlaceholder = "@ViteReactRefresh@";

    private final String template;
    private final @Nullable Vite vite;

    /**
     * Constructs a SimpleTemplateRenderer without Vite support.
     *
     * @param templatePath Classpath path to the HTML template file (e.g., "/templates/app.html").
     * @throws TemplateRenderingException if the template file cannot be loaded or read, or uses the placeholder
     *                                    of Inertia4J 1.x.
     */
    public SimpleTemplateRenderer(String templatePath) throws TemplateRenderingException {
        this(templatePath, null);
    }

    /**
     * Constructs a SimpleTemplateRenderer.
     * Loads the template from the specified classpath resource path and prepares it for rendering.
     *
     * @param templatePath Classpath path to the HTML template file (e.g., "/templates/app.html").
     * @param vite Vite integration replacing the Vite placeholders, or {@code null} to leave them untouched.
     * @throws TemplateRenderingException if the template file cannot be loaded or read, or uses the placeholder
     *                                    of Inertia4J 1.x.
     */
    public SimpleTemplateRenderer(String templatePath, @Nullable Vite vite) throws TemplateRenderingException {
        String template = loadTemplate(templatePath);

        if (template.contains(LegacyPlaceholder)) {
            throw new TemplateRenderingException(
                "Template " + templatePath + " uses the " + LegacyPlaceholder + " placeholder, which Inertia.js v3 no longer supports. "
                    + "Replace the element holding it with " + AppPlaceholder + " and add " + HeadPlaceholder + " to the <head> element."
            );
        }

        this.template = template;
        this.vite = vite;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation first replaces the Vite placeholders, then the first occurrence of each Inertia
     * placeholder in the loaded template.
     */
    @Override
    public String render(RenderedPage page) {
        String template = renderVitePlaceholders();
        int headIndex = template.indexOf(HeadPlaceholder);
        int appIndex = template.indexOf(AppPlaceholder);

        // Placeholders are located before substituting, and the later one is replaced first, so content inserted
        // for one placeholder is never scanned for the other.
        if (headIndex > appIndex) {
            String withHead = replaceAt(template, headIndex, HeadPlaceholder, page.getHead());

            return replaceAt(withHead, appIndex, AppPlaceholder, page.getBody());
        }

        String withBody = replaceAt(template, appIndex, AppPlaceholder, page.getBody());

        return replaceAt(withBody, headIndex, HeadPlaceholder, page.getHead());
    }

    private static String replaceAt(String template, int index, String placeholder, String replacement) {
        if (index < 0) {
            return template;
        }

        return template.substring(0, index) + replacement + template.substring(index + placeholder.length());
    }

    private String renderVitePlaceholders() {
        if (vite == null) {
            return template;
        }

        String withReactRefresh = template.replace(reactRefreshPlaceholder, vite.reactRefreshTag());
        Matcher matcher = vitePattern.matcher(withReactRefresh);
        StringBuilder rendered = new StringBuilder();

        while (matcher.find()) {
            String tags = vite.tags(parseEntries(matcher.group(), matcher.group(1)));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(tags));
        }

        matcher.appendTail(rendered);

        return rendered.toString();
    }

    private static String[] parseEntries(String placeholder, String entryList) {
        String[] entries = entryList.split(",", -1);

        for (int i = 0; i < entries.length; i++) {
            entries[i] = entries[i].trim();

            if (entries[i].isEmpty()) {
                throw new ViteException("Empty entry in Vite placeholder " + placeholder);
            }
        }

        return entries;
    }

    /**
     * Loads the template content from the specified classpath resource path.
     *
     * @param path The classpath path to the template file.
     * @return The content of the template file as a String.
     * @throws TemplateRenderingException if the template file cannot be found or read.
     */
    private String loadTemplate(String path) throws TemplateRenderingException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (InputStream inputStream = classLoader.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw TemplateRenderingException.notFound(path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TemplateRenderingException(path, e);
        }
    }
}
