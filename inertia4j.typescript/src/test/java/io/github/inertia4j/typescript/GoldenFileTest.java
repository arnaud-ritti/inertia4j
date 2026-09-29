package io.github.inertia4j.typescript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static io.github.inertia4j.typescript.GeneratorTestSupport.generate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GoldenFileTest {
    @Test
    void sample_matchesGoldenFile() throws IOException {
        assertEquals(expected(), generate("sample").content());
    }

    @Test
    void sample_isValidTypeScript(@TempDir Path directory) throws Exception {
        assumeTrue(npxAvailable(), "npx not available, skipping tsc check");

        Files.writeString(directory.resolve("inertia.d.ts"), generate("sample").content());
        Path core = Files.createDirectories(directory.resolve("node_modules/@inertiajs/core"));
        Files.writeString(core.resolve("package.json"), "{\"name\":\"@inertiajs/core\",\"types\":\"index.d.ts\"}");
        Files.writeString(core.resolve("index.d.ts"), "export interface InertiaConfig {}\n");
        Files.writeString(directory.resolve("usage.ts"), """
            import type { PageProps, CreateAlbumForm } from './inertia'
            import type { InertiaConfig } from '@inertiajs/core'

            const index: PageProps<'Albums/Index'> = { albums: [{ id: 1, title: 'Blue', status: 'Active' }] }
            const form: CreateAlbumForm = { title: 'Blue', status: 'Archived' }
            const shared: InertiaConfig['sharedPageProps'] = { user: null, appName: 'Inertia4J' }
            const errors: InertiaConfig['errorValueType'] = 'required'

            export { index, form, shared, errors }
            """);

        Process process = new ProcessBuilder(
            "npx", "--yes", "-p", "typescript@5.6.3", "tsc",
            "--noEmit", "--strict", "--module", "esnext", "--moduleResolution", "node", "--target", "es2020",
            "inertia.d.ts", "usage.ts"
        ).directory(directory.toFile()).redirectErrorStream(true).start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assumeTrue(process.waitFor(3, TimeUnit.MINUTES), "tsc timed out");
        assertEquals(0, process.exitValue(), output);
    }

    private static String expected() throws IOException {
        try (InputStream in = GoldenFileTest.class.getResourceAsStream("/expected/sample.d.ts")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean npxAvailable() {
        try {
            Process process = new ProcessBuilder("npx", "--version").redirectErrorStream(true).start();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
