package io.github.inertia4j.typescript;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Generates the TypeScript declaration file for the Inertia props classes of a classpath.
 * <p>
 * User classes are loaded in an isolated classloader (parent: platform classloader), so the generator
 * works whatever the versions of Inertia4J, Jackson or Kotlin used by the application.
 */
public final class TypeScriptGenerator {
    private TypeScriptGenerator() {}

    /**
     * @param options   generation options.
     * @param classpath runtime classpath of the application: class directories and jars.
     * @return generated content and warnings.
     * @throws GenerationException on fatal problems (name collisions, duplicate components, unsupported map keys, …).
     */
    public static GenerationResult generate(GeneratorOptions options, List<Path> classpath) {
        if (options.packages().isEmpty()) {
            throw new GenerationException("No packages configured: list the packages holding your props classes");
        }

        try (URLClassLoader loader = new URLClassLoader(toUrls(classpath), ClassLoader.getPlatformClassLoader())) {
            ClassScanner.Result scan = ClassScanner.scan(classpath, options.packages(), loader);
            TypeModelBuilder builder = new TypeModelBuilder(options);
            TsModel model = builder.build(scan.annotatedClasses());

            List<String> warnings = new ArrayList<>(scan.warnings());
            warnings.addAll(builder.warnings());

            return new GenerationResult(TypeScriptWriter.write(model), List.copyOf(new LinkedHashSet<>(warnings)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static URL[] toUrls(List<Path> classpath) {
        URL[] urls = new URL[classpath.size()];

        for (int i = 0; i < classpath.size(); i++) {
            try {
                urls[i] = classpath.get(i).toUri().toURL();
            } catch (MalformedURLException e) {
                throw new GenerationException("Invalid classpath entry " + classpath.get(i), e);
            }
        }

        return urls;
    }
}
