package io.github.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A chunk of the Vite manifest, mirroring Vite's {@code ManifestChunk} interface.
 *
 * @see <a href="https://vite.dev/guide/backend-integration.html">Vite backend integration</a>
 */
@NullMarked
public class ManifestChunk {
    private final String file;
    private final @Nullable String src;
    private final @Nullable String name;
    private final boolean entry;
    private final boolean dynamicEntry;
    private final List<String> imports;
    private final List<String> dynamicImports;
    private final List<String> css;
    private final List<String> assets;
    private final Map<String, String> attributes;

    ManifestChunk(String key, Map<?, ?> json) {
        Object file = json.get("file");

        if (!(file instanceof String)) {
            throw new ViteException("Invalid Vite manifest: chunk '" + key + "' has no file");
        }

        this.file = (String) file;
        this.src = optionalString(key, json, "src");
        this.name = optionalString(key, json, "name");
        this.entry = Boolean.TRUE.equals(json.get("isEntry"));
        this.dynamicEntry = Boolean.TRUE.equals(json.get("isDynamicEntry"));
        this.imports = stringList(key, json, "imports");
        this.dynamicImports = stringList(key, json, "dynamicImports");
        this.css = stringList(key, json, "css");
        this.assets = stringList(key, json, "assets");
        this.attributes = stringAttributes(json);
    }

    /**
     * @return output file, relative to the build directory.
     */
    public String getFile() {
        return file;
    }

    /**
     * @return input file, relative to the Vite root, if any.
     */
    public @Nullable String getSrc() {
        return src;
    }

    /**
     * @return chunk name, if any.
     */
    public @Nullable String getName() {
        return name;
    }

    /**
     * @return whether the chunk is an entry point.
     */
    public boolean isEntry() {
        return entry;
    }

    /**
     * @return whether the chunk is loaded through a dynamic import.
     */
    public boolean isDynamicEntry() {
        return dynamicEntry;
    }

    /**
     * @return manifest keys of statically imported chunks.
     */
    public List<String> getImports() {
        return imports;
    }

    /**
     * @return manifest keys of dynamically imported chunks.
     */
    public List<String> getDynamicImports() {
        return dynamicImports;
    }

    /**
     * @return stylesheets of the chunk, relative to the build directory.
     */
    public List<String> getCss() {
        return css;
    }

    /**
     * @return assets of the chunk, relative to the build directory.
     */
    public List<String> getAssets() {
        return assets;
    }

    /**
     * @return Subresource Integrity hash of the output file, such as {@code sha384-...}, as written in the
     * {@code integrity} field by {@code vite-plugin-manifest-sri}, if any.
     */
    public @Nullable String getIntegrity() {
        return getAttribute(ViteConfig.DefaultIntegrityKey);
    }

    /**
     * Reads a string field of the chunk, including fields added by Vite plugins.
     *
     * @param field name of the field, such as {@code integrity}.
     * @return the value of the field, or {@code null} when the chunk has no such string field.
     */
    public @Nullable String getAttribute(String field) {
        return attributes.get(field);
    }

    private static Map<String, String> stringAttributes(Map<?, ?> json) {
        Map<String, String> attributes = new LinkedHashMap<>();

        json.forEach((field, value) -> {
            if (value instanceof String) {
                attributes.put((String) field, (String) value);
            }
        });

        return Collections.unmodifiableMap(attributes);
    }

    private static @Nullable String optionalString(String key, Map<?, ?> json, String field) {
        Object value = json.get(field);

        if (value == null) {
            return null;
        }

        if (!(value instanceof String)) {
            throw new ViteException("Invalid Vite manifest: chunk '" + key + "' has a non-string '" + field + "'");
        }

        return (String) value;
    }

    private static List<String> stringList(String key, Map<?, ?> json, String field) {
        Object value = json.get(field);

        if (value == null) {
            return List.of();
        }

        if (!(value instanceof List)) {
            throw new ViteException("Invalid Vite manifest: chunk '" + key + "' has a non-array '" + field + "'");
        }

        List<String> strings = new ArrayList<>();

        for (Object item : (List<?>) value) {
            if (!(item instanceof String)) {
                throw new ViteException(
                    "Invalid Vite manifest: chunk '" + key + "' has a non-string value in '" + field + "'"
                );
            }

            strings.add((String) item);
        }

        return Collections.unmodifiableList(strings);
    }
}
