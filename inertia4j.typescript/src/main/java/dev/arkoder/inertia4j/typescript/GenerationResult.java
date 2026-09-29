package dev.arkoder.inertia4j.typescript;

import java.util.List;

/**
 * Output of a TypeScript generation run.
 *
 * @param content  content of the {@code .d.ts} file.
 * @param warnings non-fatal problems, e.g. types mapped to {@code unknown}.
 */
public record GenerationResult(String content, List<String> warnings) {}
