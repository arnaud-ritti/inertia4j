package io.github.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Produces the HTML tags loading Vite entries, following the Vite backend integration guide,
 * and the Inertia asset version derived from the Vite manifest.
 * Thread-safe; one instance is meant to be shared by the whole application.
 *
 * @see <a href="https://vite.dev/guide/backend-integration.html">Vite backend integration</a>
 */
@NullMarked
public class Vite {
    private static final String defaultVersion = "1";
    private static final Pattern stylesheetPattern =
        Pattern.compile("\\.(css|less|sass|scss|styl|stylus|pcss|postcss)(\\?.*)?$");

    private final ViteConfig config;
    private volatile @Nullable LoadedManifest loadedManifest;

    /**
     * @param config integration settings.
     */
    public Vite(ViteConfig config) {
        this.config = config;
    }

    /**
     * @return integration settings.
     */
    public ViteConfig getConfig() {
        return config;
    }

    /**
     * Renders the tags loading the given entries.
     *
     * @param entries manifest keys, such as {@code src/main.tsx}; a leading slash is ignored.
     * @return tags separated by new lines.
     * @throws ViteException if no entry is given, the manifest is missing or invalid, or an entry is unknown.
     */
    public String tags(String... entries) {
        if (entries.length == 0) {
            throw new ViteException("At least one Vite entry is required");
        }

        List<String> normalisedEntries = new ArrayList<>();

        for (String entry : entries) {
            normalisedEntries.add(stripLeadingSlash(entry));
        }

        return productionTags(normalisedEntries);
    }

    /**
     * @return SHA-256 of the manifest, or {@code "1"} when the manifest is missing or invalid.
     */
    public String version() {
        try {
            return manifest().hash;
        } catch (ViteException e) {
            return defaultVersion;
        }
    }

    private String productionTags(List<String> entries) {
        ViteManifest manifest = manifest().manifest;
        Set<String> stylesheets = new LinkedHashSet<>();
        Set<String> entryScripts = new LinkedHashSet<>();
        Set<String> preloads = new LinkedHashSet<>();

        for (String entry : entries) {
            ManifestChunk chunk = manifest.chunk(entry).orElseThrow(() -> new ViteException(
                "Unable to locate '" + entry + "' in the Vite manifest. Available entries: "
                    + String.join(", ", manifest.entries())
            ));

            collectStylesheets(manifest, entry, chunk, stylesheets, new HashSet<>());

            if (isStylesheet(chunk.getFile())) {
                stylesheets.add(url(chunk.getFile()));
            } else {
                entryScripts.add(url(chunk.getFile()));
            }

            collectPreloads(manifest, entry, chunk, preloads, new HashSet<>());
        }

        preloads.removeAll(entryScripts);

        List<String> tags = new ArrayList<>();
        stylesheets.forEach(href -> tags.add(stylesheetTag(href)));
        entryScripts.forEach(src -> tags.add(scriptTag(src)));
        preloads.forEach(href -> tags.add("<link rel=\"modulepreload\" href=\"" + escapeHtml(href) + "\">"));

        return String.join("\n", tags);
    }

    private void collectStylesheets(
        ViteManifest manifest,
        String key,
        ManifestChunk chunk,
        Set<String> stylesheets,
        Set<String> visited
    ) {
        if (!visited.add(key)) {
            return;
        }

        chunk.getCss().forEach(file -> stylesheets.add(url(file)));

        for (String importKey : chunk.getImports()) {
            collectStylesheets(manifest, importKey, importedChunk(manifest, key, importKey), stylesheets, visited);
        }
    }

    private void collectPreloads(
        ViteManifest manifest,
        String key,
        ManifestChunk chunk,
        Set<String> preloads,
        Set<String> visited
    ) {
        for (String importKey : chunk.getImports()) {
            if (!visited.add(importKey)) {
                continue;
            }

            ManifestChunk imported = importedChunk(manifest, key, importKey);
            preloads.add(url(imported.getFile()));
            collectPreloads(manifest, importKey, imported, preloads, visited);
        }
    }

    private static ManifestChunk importedChunk(ViteManifest manifest, String key, String importKey) {
        return manifest.chunk(importKey).orElseThrow(() -> new ViteException(
            "Invalid Vite manifest: chunk '" + key + "' imports unknown chunk '" + importKey + "'"
        ));
    }

    private LoadedManifest manifest() {
        LoadedManifest current = loadedManifest;

        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (loadedManifest == null) {
                loadedManifest = loadManifest();
            }

            return loadedManifest;
        }
    }

    private LoadedManifest loadManifest() {
        String path = config.getManifestPath();

        try (InputStream inputStream = classLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new ViteException(
                    "Vite manifest not found at classpath:" + path
                        + ". Start the Vite dev server or run the frontend build."
                );
            }

            byte[] bytes = inputStream.readAllBytes();

            return new LoadedManifest(ViteManifest.parse(new String(bytes, StandardCharsets.UTF_8)), sha256(bytes));
        } catch (IOException e) {
            throw new ViteException("Unable to read Vite manifest at classpath:" + path, e);
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        if (contextClassLoader != null) {
            return contextClassLoader;
        }

        return Vite.class.getClassLoader();
    }

    private String url(String file) {
        return config.getPublicPath() + file;
    }

    private static boolean isStylesheet(String path) {
        return stylesheetPattern.matcher(path).find();
    }

    private static String scriptTag(String src) {
        return "<script type=\"module\" src=\"" + escapeHtml(src) + "\"></script>";
    }

    private static String stylesheetTag(String href) {
        return "<link rel=\"stylesheet\" href=\"" + escapeHtml(href) + "\">";
    }

    private static String stripLeadingSlash(String path) {
        String stripped = path;

        while (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }

        return stripped;
    }

    private static String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);

            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }

            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static class LoadedManifest {
        private final ViteManifest manifest;
        private final String hash;

        private LoadedManifest(ViteManifest manifest, String hash) {
            this.manifest = manifest;
            this.hash = hash;
        }
    }
}
