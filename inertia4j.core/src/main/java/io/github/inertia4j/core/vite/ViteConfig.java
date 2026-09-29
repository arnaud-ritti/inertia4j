package io.github.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Settings of the Vite integration. Defaults match the {@code vite.config.ts} documented in {@code docs/vite.md}.
 */
@NullMarked
public class ViteConfig {
    private final Path hotFile;
    private final String buildDirectory;
    private final String manifestPath;
    private final String publicPath;

    private ViteConfig(Path hotFile, String buildDirectory, String manifestPath, String publicPath) {
        this.hotFile = hotFile;
        this.buildDirectory = buildDirectory;
        this.manifestPath = manifestPath;
        this.publicPath = publicPath;
    }

    /**
     * @return a builder initialised with the defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the default configuration.
     */
    public static ViteConfig defaults() {
        return builder().build();
    }

    /**
     * @return file whose presence, written by the Vite dev server, enables dev mode.
     */
    public Path getHotFile() {
        return hotFile;
    }

    /**
     * @return classpath directory containing the build output, without leading or trailing slash.
     */
    public String getBuildDirectory() {
        return buildDirectory;
    }

    /**
     * @return classpath location of the manifest, without leading slash.
     */
    public String getManifestPath() {
        return manifestPath;
    }

    /**
     * @return URL prefix of built files, starting and ending with a slash.
     */
    public String getPublicPath() {
        return publicPath;
    }

    /**
     * Builder of {@link ViteConfig}.
     */
    public static class Builder {
        private Path hotFile = Path.of("vite.hot");
        private String buildDirectory = "static/build";
        private @Nullable String manifestPath;
        private String publicPath = "/build/";

        private Builder() {
        }

        /**
         * @param hotFile file whose presence enables dev mode.
         * @return this builder.
         */
        public Builder hotFile(Path hotFile) {
            this.hotFile = hotFile;
            return this;
        }

        /**
         * @param buildDirectory classpath directory containing the build output.
         * @return this builder.
         */
        public Builder buildDirectory(String buildDirectory) {
            this.buildDirectory = buildDirectory;
            return this;
        }

        /**
         * @param manifestPath classpath location of the manifest, or {@code null} for
         *                     {@code {buildDirectory}/.vite/manifest.json}.
         * @return this builder.
         */
        public Builder manifestPath(@Nullable String manifestPath) {
            this.manifestPath = manifestPath;
            return this;
        }

        /**
         * @param publicPath URL prefix of built files.
         * @return this builder.
         */
        public Builder publicPath(String publicPath) {
            this.publicPath = publicPath;
            return this;
        }

        /**
         * @return the configuration.
         */
        public ViteConfig build() {
            String normalisedBuildDirectory = trimSlashes(buildDirectory);

            if (normalisedBuildDirectory.isEmpty()) {
                throw new ViteException("Vite build directory must not be empty");
            }

            String normalisedManifestPath = manifestPath == null
                ? normalisedBuildDirectory + "/.vite/manifest.json"
                : trimSlashes(manifestPath);

            return new ViteConfig(
                hotFile,
                normalisedBuildDirectory,
                normalisedManifestPath,
                normalisePublicPath(publicPath)
            );
        }

        private static String trimSlashes(String path) {
            int start = 0;
            int end = path.length();

            while (start < end && path.charAt(start) == '/') {
                start++;
            }

            while (end > start && path.charAt(end - 1) == '/') {
                end--;
            }

            return path.substring(start, end);
        }

        private static String normalisePublicPath(String path) {
            String trimmed = trimSlashes(path);

            if (trimmed.isEmpty()) {
                return "/";
            }

            return "/" + trimmed + "/";
        }
    }
}
