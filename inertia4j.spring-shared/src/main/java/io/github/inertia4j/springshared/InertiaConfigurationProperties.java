package io.github.inertia4j.springshared;

import io.github.inertia4j.core.vite.ViteConfig;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuration properties for Inertia4j integration with Spring Boot.
 * Allows setting the template path, default history encryption behavior and the Vite integration via application
 * properties. Properties are prefixed with `inertia`.
 * <p>
 * Example `application.properties`:
 * <pre>
 * inertia.template-path=templates/my-app.html
 * inertia.encrypt-history=true
 * inertia.vite.build-directory=static/build
 * </pre>
 */
@ConfigurationProperties(prefix = "inertia")
public class InertiaConfigurationProperties {
    private static final String defaultTemplatePath = "templates/app.html";
    private static final boolean defaultEncryptHistory = false;

    /**
     * The classpath path to the main HTML template file used by the default {@link io.github.inertia4j.core.SimpleTemplateRenderer}.
     * Corresponds to the `inertia.template-path` property.
     */
    final String templatePath;
    /**
     * Default value for the encryptHistory flag, determining whether browser history state should be encrypted.
     * Corresponds to the `inertia.encrypt-history` property.
     * @see <a href="https://inertiajs.com/history-encryption">Inertia History Encryption</a>
     */
    final boolean encryptHistory;
    /**
     * Vite integration settings, bound from the `inertia.vite.*` properties.
     */
    final ViteProperties vite;

    /**
     * Constructor used by Spring Boot for property binding.
     * @param templatePath Value of `inertia.template-path`.
     * @param encryptHistory Value of `inertia.encrypt-history`.
     * @param vite Values of `inertia.vite.*`.
     */
    @ConstructorBinding
    public InertiaConfigurationProperties(
        @DefaultValue(defaultTemplatePath) String templatePath,
        @DefaultValue("false") boolean encryptHistory,
        @DefaultValue ViteProperties vite
    ) {
        this.templatePath = templatePath;
        this.encryptHistory = encryptHistory;
        this.vite = vite;
    }

    /**
     * Constructor using the default Vite settings.
     * @param templatePath The template path.
     * @param encryptHistory The encryptHistory flag value.
     */
    public InertiaConfigurationProperties(String templatePath, boolean encryptHistory) {
        this(templatePath, encryptHistory, new ViteProperties());
    }

    /**
     * Constructor using default `encryptHistory`.
     * @param templatePath The template path.
     */
    public InertiaConfigurationProperties(String templatePath) {
        this(templatePath, defaultEncryptHistory);
    }

    /**
     * Constructor using default `templatePath`.
     * @param encryptHistory The encryptHistory flag value.
     */
    public InertiaConfigurationProperties(boolean encryptHistory) {
        this(defaultTemplatePath, encryptHistory);
    }

    /**
     * Constructor using default values for both `templatePath` and `encryptHistory`.
     */
    public InertiaConfigurationProperties() {
        this(defaultTemplatePath, defaultEncryptHistory);
    }

    /**
     * Vite integration settings, prefixed with `inertia.vite`.
     */
    public static class ViteProperties {
        /**
         * Whether the Vite integration is enabled: placeholders, asset version and asset serving.
         */
        final boolean enabled;
        /**
         * File whose presence, written by the Vite dev server, enables dev mode.
         */
        final String hotFile;
        /**
         * Classpath directory containing the Vite build output.
         */
        final String buildDirectory;
        /**
         * Classpath location of the Vite manifest, `null` for `{build-directory}/.vite/manifest.json`.
         */
        final @Nullable String manifest;
        /**
         * URL prefix under which built files are served.
         */
        final String publicPath;
        /**
         * `max-age` of the `Cache-Control` header sent with built files.
         */
        final Duration cacheMaxAge;

        /**
         * Constructor used by Spring Boot for property binding.
         * @param enabled Value of `inertia.vite.enabled`.
         * @param hotFile Value of `inertia.vite.hot-file`.
         * @param buildDirectory Value of `inertia.vite.build-directory`.
         * @param manifest Value of `inertia.vite.manifest`.
         * @param publicPath Value of `inertia.vite.public-path`.
         * @param cacheMaxAge Value of `inertia.vite.cache-max-age`.
         */
        @ConstructorBinding
        public ViteProperties(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("vite.hot") String hotFile,
            @DefaultValue("static/build") String buildDirectory,
            @Nullable String manifest,
            @DefaultValue("/build/") String publicPath,
            @DefaultValue("365d") Duration cacheMaxAge
        ) {
            this.enabled = enabled;
            this.hotFile = hotFile;
            this.buildDirectory = buildDirectory;
            this.manifest = manifest;
            this.publicPath = publicPath;
            this.cacheMaxAge = cacheMaxAge;
        }

        /**
         * Constructor using the default values.
         */
        public ViteProperties() {
            this(true, "vite.hot", "static/build", null, "/build/", Duration.ofDays(365));
        }

        ViteConfig toViteConfig() {
            return ViteConfig.builder()
                .hotFile(Path.of(hotFile))
                .buildDirectory(buildDirectory)
                .manifestPath(manifest)
                .publicPath(publicPath)
                .build();
        }
    }
}
