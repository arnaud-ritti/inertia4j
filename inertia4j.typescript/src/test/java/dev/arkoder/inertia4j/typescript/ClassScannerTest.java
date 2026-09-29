package dev.arkoder.inertia4j.typescript;

import dev.arkoder.inertia4j.typescript.fixtures.scan.ScannedPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassScannerTest {
    private static final String ScanPackage = "dev.arkoder.inertia4j.typescript.fixtures.scan";

    @Test
    void scan_directory_keepsAnnotatedClassesUnderPackages() throws Exception {
        Path classesDir = Path.of(ScannedPage.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        ClassScanner.Result result = ClassScanner.scan(List.of(classesDir), List.of(ScanPackage), getClass().getClassLoader());

        assertEquals(
            List.of(ScanPackage + ".ScannedForm", ScanPackage + ".ScannedPage", ScanPackage + ".ScannedShared"),
            result.annotatedClasses().stream().map(Class::getName).toList()
        );
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void scan_jar_findsAnnotatedClasses(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("fixtures.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            copyClass(out, ScannedPage.class);
        }

        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            ClassScanner.Result result = ClassScanner.scan(List.of(jar), List.of(ScanPackage), loader);

            assertEquals(List.of(ScanPackage + ".ScannedPage"), result.annotatedClasses().stream().map(Class::getName).toList());
        }
    }

    @Test
    void scan_unloadableClass_isSkippedWithWarning(@TempDir Path tempDir) throws Exception {
        Path brokenClass = tempDir.resolve("dev/arkoder/inertia4j/typescript/fixtures/broken/Broken.class");
        Files.createDirectories(brokenClass.getParent());
        Files.write(brokenClass, new byte[]{1, 2, 3});

        try (URLClassLoader loader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, getClass().getClassLoader())) {
            ClassScanner.Result result = ClassScanner.scan(
                List.of(tempDir),
                List.of("dev.arkoder.inertia4j.typescript.fixtures.broken"),
                loader
            );

            assertTrue(result.annotatedClasses().isEmpty());
            assertEquals(1, result.warnings().size());
            assertTrue(result.warnings().get(0).contains("dev.arkoder.inertia4j.typescript.fixtures.broken.Broken"));
        }
    }

    @Test
    void scan_classCompiledForNewerJvm_failsNamingClassAndJvm(@TempDir Path tempDir) throws Exception {
        Path futureClass = tempDir.resolve("dev/arkoder/inertia4j/typescript/fixtures/future/Future.class");
        Files.createDirectories(futureClass.getParent());
        Files.write(futureClass, new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0x7F, 0x7F});

        try (URLClassLoader loader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, getClass().getClassLoader())) {
            GenerationException exception = assertThrows(
                GenerationException.class,
                () -> ClassScanner.scan(List.of(tempDir), List.of("dev.arkoder.inertia4j.typescript.fixtures.future"), loader)
            );

            assertTrue(exception.getMessage().contains("dev.arkoder.inertia4j.typescript.fixtures.future.Future"), exception.getMessage());
            assertTrue(exception.getMessage().contains("Gradle JVM is older"), exception.getMessage());
        }
    }

    @Test
    void inPackages_matchesPackageAndSubpackagesOnly() {
        assertTrue(ClassScanner.inPackages("com.example.Foo", List.of("com.example")));
        assertTrue(ClassScanner.inPackages("com.example.sub.Foo", List.of("com.example")));
        assertFalse(ClassScanner.inPackages("com.examples.Foo", List.of("com.example")));
    }

    private static void copyClass(JarOutputStream out, Class<?> type) throws IOException {
        String entryName = type.getName().replace('.', '/') + ".class";
        out.putNextEntry(new JarEntry(entryName));
        try (InputStream in = type.getClassLoader().getResourceAsStream(entryName)) {
            in.transferTo(out);
        }
        out.closeEntry();
    }
}
