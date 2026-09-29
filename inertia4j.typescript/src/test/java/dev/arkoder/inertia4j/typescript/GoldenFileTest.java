package dev.arkoder.inertia4j.typescript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static dev.arkoder.inertia4j.typescript.GeneratorTestSupport.generate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
            npx().orElseThrow().toString(), "--yes", "-p", "typescript@5.6.3", "tsc",
            "--noEmit", "--strict", "--module", "esnext", "--moduleResolution", "node", "--target", "es2020",
            "inertia.d.ts", "usage.ts"
        ).directory(directory.toFile()).redirectErrorStream(true).start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assumeTrue(process.waitFor(3, TimeUnit.MINUTES), "tsc timed out");
        assertEquals(0, process.exitValue(), output);
    }

    @Test
    void routes_compileAndBuildUrls(@TempDir Path directory) throws Exception {
        assumeTrue(npxAvailable(), "npx not available, skipping tsc check");
        assumeTrue(executable("node").isPresent(), "node not available, skipping routes check");

        Files.writeString(directory.resolve("routes.ts"), RoutesWriter.write(List.of(
            new RouteEntry(List.of("UsersController", "index"), List.of("get"), "/users"),
            new RouteEntry(List.of("UsersController", "show"), List.of("get"), "/users/{id}"),
            new RouteEntry(List.of("UsersController", "update"), List.of("put", "patch"), "/users/{id}"),
            new RouteEntry(List.of("files", "show"), List.of("get"), "/files/{*path}"),
            new RouteEntry(List.of("reports", "month"), List.of("get"), "/reports/{year}/{month?}")
        )));
        Files.writeString(directory.resolve("usage.ts"), """
            import { UsersController, files, reports } from './routes'
            import type { RouteDefinition, RouteFormDefinition } from './routes'

            function expect(actual: unknown, expected: unknown): void {
              if (JSON.stringify(actual) !== JSON.stringify(expected)) {
                throw new Error(`Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`)
              }
            }

            const index: RouteDefinition<'get'> = UsersController.index()
            expect(index, { url: '/users', method: 'get' })
            expect(UsersController.index.url({ query: { page: 2, tags: ['a', 'b'], empty: null } }), '/users?page=2&tags=a&tags=b')
            expect(UsersController.show({ id: 5 }), { url: '/users/5', method: 'get' })
            expect(UsersController.show.url({ id: 'a b' }), '/users/a%20b')

            const update: RouteDefinition<'put'> = UsersController.update({ id: 5 })
            expect(update.method, 'put')
            expect(UsersController.update.patch({ id: 5 }), { url: '/users/5', method: 'patch' })
            const form: RouteFormDefinition = UsersController.update.form({ id: 5 })
            expect(form, { action: '/users/5?_method=PUT', method: 'post' })
            expect(UsersController.update.definition, { methods: ['put', 'patch'], url: '/users/{id}' })

            expect(files.show.url({ path: 'docs/a b.pdf' }), '/files/docs/a%20b.pdf')
            expect(reports.month.url({ year: 2026 }), '/reports/2026')
            expect(reports.month.url({ year: 2026, month: 9 }), '/reports/2026/9')

            function typeErrors(): void {
              // @ts-expect-error the id parameter is required
              UsersController.show()
              // @ts-expect-error index takes no parameters
              UsersController.index({ id: 1 }, {})
            }
            void typeErrors

            console.log('routes ok')
            """);

        Process compile = new ProcessBuilder(
            npx().orElseThrow().toString(), "--yes", "-p", "typescript@5.6.3", "tsc",
            "--strict", "--module", "commonjs", "--target", "es2020", "--lib", "es2020,dom", "--outDir", "out",
            "routes.ts", "usage.ts"
        ).directory(directory.toFile()).redirectErrorStream(true).start();

        String compileOutput = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assumeTrue(compile.waitFor(3, TimeUnit.MINUTES), "tsc timed out");
        assertEquals(0, compile.exitValue(), compileOutput);

        Process run = new ProcessBuilder(executable("node").orElseThrow().toString(), "out/usage.js")
            .directory(directory.toFile()).redirectErrorStream(true).start();

        String runOutput = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(run.waitFor(1, TimeUnit.MINUTES), "node timed out");
        assertEquals(0, run.exitValue(), runOutput);
        assertEquals("routes ok", runOutput.trim());
    }

    private static String expected() throws IOException {
        try (InputStream in = GoldenFileTest.class.getResourceAsStream("/expected/sample.d.ts")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Optional<Path> npx() {
        return executable("npx");
    }

    private static Optional<Path> executable(String name) {
        String executable = System.getProperty("os.name").startsWith("Windows") && !name.equals("node") ? name + ".cmd" : name;

        return Arrays.stream(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator))
            .filter(directory -> !directory.isBlank())
            .map(directory -> Path.of(directory, executable).toAbsolutePath())
            .filter(Files::isExecutable)
            .findFirst();
    }

    private static boolean npxAvailable() {
        if (npx().isEmpty()) {
            return false;
        }

        try {
            Process process = new ProcessBuilder(npx().get().toString(), "--version").redirectErrorStream(true).start();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
