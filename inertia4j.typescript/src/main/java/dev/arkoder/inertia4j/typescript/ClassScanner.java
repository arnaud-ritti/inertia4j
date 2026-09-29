package dev.arkoder.inertia4j.typescript;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Finds classes annotated with {@code @InertiaPage}, {@code @InertiaShared}, {@code @InertiaForm} or {@code @InertiaFlash}
 * under the configured packages of a classpath.
 */
final class ClassScanner {
    private static final Set<String> RoleAnnotations = Set.of(
        Annotations.InertiaPage,
        Annotations.InertiaShared,
        Annotations.InertiaForm,
        Annotations.InertiaFlash
    );

    record Result(List<Class<?>> annotatedClasses, List<String> warnings) {}

    private ClassScanner() {}

    static boolean inPackages(String className, List<String> packages) {
        return packages.stream().anyMatch(packageName -> className.startsWith(packageName + "."));
    }

    static Result scan(List<Path> classpath, List<String> packages, ClassLoader loader) {
        Set<String> classNames = new TreeSet<>();
        for (Path entry : classpath) {
            classNames.addAll(classNamesIn(entry));
        }

        List<Class<?>> annotatedClasses = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String className : classNames) {
            if (!inPackages(className, packages)) {
                continue;
            }

            try {
                Class<?> type = Class.forName(className, false, loader);
                if (hasRoleAnnotation(type)) {
                    annotatedClasses.add(type);
                }
            } catch (UnsupportedClassVersionError e) {
                throw new GenerationException(
                    "Cannot load " + className + ": the Gradle JVM is older than the JVM the classes were compiled for ("
                        + e.getMessage() + "). Run Gradle with a JDK at least as recent as the compiled classes.",
                    e
                );
            } catch (ClassNotFoundException | LinkageError e) {
                warnings.add("Skipped class " + className + ": " + e);
            }
        }

        annotatedClasses.sort(Comparator.comparing(Class::getName));

        return new Result(annotatedClasses, warnings);
    }

    private static boolean hasRoleAnnotation(Class<?> type) {
        return RoleAnnotations.stream().anyMatch(annotation -> Annotations.has(type, annotation));
    }

    private static List<String> classNamesIn(Path entry) {
        if (Files.isDirectory(entry)) {
            return classNamesInDirectory(entry);
        }

        if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
            return classNamesInJar(entry);
        }

        return List.of();
    }

    private static List<String> classNamesInDirectory(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files
                .filter(file -> file.toString().endsWith(".class"))
                .map(file -> toClassName(directory.relativize(file).toString().replace('\\', '/')))
                .filter(ClassScanner::isRegularClassName)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> classNamesInJar(Path jar) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            return jarFile.stream()
                .map(JarEntry::getName)
                .filter(name -> name.endsWith(".class") && !name.startsWith("META-INF/"))
                .map(ClassScanner::toClassName)
                .filter(ClassScanner::isRegularClassName)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String toClassName(String path) {
        return path.substring(0, path.length() - ".class".length()).replace('/', '.');
    }

    private static boolean isRegularClassName(String className) {
        return !className.endsWith("module-info") && !className.endsWith("package-info");
    }
}
