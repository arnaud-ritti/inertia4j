package dev.arkoder.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parsed {@code .vite/manifest.json}, mapping manifest keys to chunks.
 */
@NullMarked
public class ViteManifest {
    private final Map<String, ManifestChunk> chunks;
    private final Map<String, ManifestChunk> chunksByFile;

    private ViteManifest(Map<String, ManifestChunk> chunks) {
        this.chunks = chunks;
        this.chunksByFile = new LinkedHashMap<>();

        chunks.values().forEach(chunk -> chunksByFile.putIfAbsent(chunk.getFile(), chunk));
    }

    /**
     * Parses the content of a Vite manifest.
     *
     * @param json manifest content.
     * @return parsed manifest.
     * @throws ViteException if the content is not a valid manifest.
     */
    public static ViteManifest parse(String json) {
        Object root = ManifestJsonReader.read(json);

        if (!(root instanceof Map)) {
            throw new ViteException("Invalid Vite manifest: expected a JSON object");
        }

        Map<String, ManifestChunk> chunks = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : ((Map<?, ?>) root).entrySet()) {
            String key = (String) entry.getKey();

            if (!(entry.getValue() instanceof Map)) {
                throw new ViteException("Invalid Vite manifest: chunk '" + key + "' is not a JSON object");
            }

            chunks.put(key, new ManifestChunk(key, (Map<?, ?>) entry.getValue()));
        }

        return new ViteManifest(Collections.unmodifiableMap(chunks));
    }

    /**
     * @param key manifest key, such as {@code src/main.tsx}.
     * @return the chunk, or empty if the manifest has no such key.
     */
    public Optional<ManifestChunk> chunk(String key) {
        return Optional.ofNullable(chunks.get(key));
    }

    /**
     * @param file output file, relative to the build directory, such as {@code assets/main-BRBmoGS9.js}.
     * @return the first chunk whose output is the given file, or empty if no chunk has this output.
     */
    public Optional<ManifestChunk> chunkByFile(String file) {
        return Optional.ofNullable(chunksByFile.get(file));
    }

    /**
     * @return keys of entry chunks, in manifest order.
     */
    public Set<String> entries() {
        Set<String> entries = new LinkedHashSet<>();

        chunks.forEach((key, chunk) -> {
            if (chunk.isEntry()) {
                entries.add(key);
            }
        });

        return Collections.unmodifiableSet(entries);
    }
}
