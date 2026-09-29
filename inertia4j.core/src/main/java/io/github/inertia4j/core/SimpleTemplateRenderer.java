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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple {@link TemplateRenderer} implementation used by default if no specific renderer is provided.
 * It loads a template file from the classpath and replaces the {@value #HeadPlaceholder} placeholder with the
 * server-side rendered head elements, and the {@value #AppPlaceholder} placeholder with the page object script
 * element and the application root element.
 * <p>
 * Content placed between {@value #HeadPlaceholder} and {@value #EndHeadPlaceholder}, such as a default
 * {@code <title>}, is a fallback rendered only when the page has no server-side rendered head elements, and replaced
 * by them otherwise.
 * <p>
 * When given a {@link Vite} instance, it also replaces the <code>@Vite(entry, ...)@</code>,
 * <code>@ViteReactRefresh@</code> and <code>@ViteAsset(path)@</code> placeholders with the tags loading the frontend
 * and the URL of a file processed by Vite.
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

    /**
     * Closes the fallback content opened by {@value #HeadPlaceholder}, rendered when the page has no server-side
     * rendered head elements.
     */
    public static final String EndHeadPlaceholder = "@EndInertiaHead@";

    private static final String LegacyPlaceholder = "@PageObject@";
    private static final Pattern vitePattern = Pattern.compile("@Vite\\(([^)]*)\\)@");
    private static final Pattern viteAssetPattern = Pattern.compile("@ViteAsset\\(([^)]*)\\)@");
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

        validateHeadFallback(templatePath, template);

        this.template = template;
        this.vite = vite;
    }

    private static void validateHeadFallback(String templatePath, String template) {
        int endIndex = template.indexOf(EndHeadPlaceholder);

        if (endIndex < 0) {
            return;
        }

        int headIndex = template.indexOf(HeadPlaceholder);

        if (headIndex < 0 || headIndex > endIndex) {
            throw new TemplateRenderingException(
                "Template " + templatePath + " uses " + EndHeadPlaceholder + " without a preceding " + HeadPlaceholder
            );
        }

        int appIndex = template.indexOf(AppPlaceholder);

        if (appIndex > headIndex && appIndex < endIndex) {
            throw new TemplateRenderingException(
                "Template " + templatePath + " has " + AppPlaceholder + " inside the " + HeadPlaceholder + " fallback"
            );
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation first replaces the Vite placeholders, then the first occurrence of each Inertia
     * placeholder in the loaded template. The head placeholder, with its fallback content when closed by
     * {@value #EndHeadPlaceholder}, is replaced by the server-side rendered head elements, or by the fallback content
     * when there are none.
     */
    @Override
    public String render(RenderedPage page) {
        String template = renderVitePlaceholders();
        Region head = headRegion(template);
        Region app = Region.of(template, AppPlaceholder);
        String headContent = page.getHead().isEmpty() ? head.fallback(template) : page.getHead();

        // Placeholders are located before substituting, and the later one is replaced first, so content inserted
        // for one placeholder is never scanned for the other.
        if (head.start > app.start) {
            String withHead = head.replace(template, headContent);

            return app.replace(withHead, page.getBody());
        }

        String withBody = app.replace(template, page.getBody());

        return head.replace(withBody, headContent);
    }

    private static Region headRegion(String template) {
        Region head = Region.of(template, HeadPlaceholder);

        if (head.start < 0) {
            return head;
        }

        int endIndex = template.indexOf(EndHeadPlaceholder, head.end);

        if (endIndex < 0) {
            return head;
        }

        return new Region(head.start, endIndex + EndHeadPlaceholder.length(), head.end, endIndex);
    }

    private String renderVitePlaceholders() {
        if (vite == null) {
            return template;
        }

        String withReactRefresh = template.replace(reactRefreshPlaceholder, vite.reactRefreshTag());
        String withAssets = replaceAll(
            withReactRefresh,
            viteAssetPattern,
            matcher -> vite.asset(parseAsset(matcher.group(), matcher.group(1)))
        );

        return replaceAll(
            withAssets,
            vitePattern,
            matcher -> vite.tags(parseEntries(matcher.group(), matcher.group(1)))
        );
    }

    private static String replaceAll(String template, Pattern pattern, Function<Matcher, String> replacement) {
        Matcher matcher = pattern.matcher(template);
        StringBuilder rendered = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement.apply(matcher)));
        }

        matcher.appendTail(rendered);

        return rendered.toString();
    }

    private static String parseAsset(String placeholder, String path) {
        String trimmed = path.trim();

        if (trimmed.isEmpty()) {
            throw new ViteException("Empty path in Vite asset placeholder " + placeholder);
        }

        return trimmed;
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

    /**
     * Location of a placeholder in a template, including its fallback content, if any.
     */
    private static class Region {
        private final int start;
        private final int end;
        private final int fallbackStart;
        private final int fallbackEnd;

        private Region(int start, int end, int fallbackStart, int fallbackEnd) {
            this.start = start;
            this.end = end;
            this.fallbackStart = fallbackStart;
            this.fallbackEnd = fallbackEnd;
        }

        private static Region of(String template, String placeholder) {
            int start = template.indexOf(placeholder);

            if (start < 0) {
                return new Region(-1, -1, -1, -1);
            }

            int end = start + placeholder.length();

            return new Region(start, end, end, end);
        }

        private String fallback(String template) {
            if (start < 0) {
                return "";
            }

            return template.substring(fallbackStart, fallbackEnd);
        }

        private String replace(String template, String replacement) {
            if (start < 0) {
                return template;
            }

            return template.substring(0, start) + replacement + template.substring(end);
        }
    }
}
