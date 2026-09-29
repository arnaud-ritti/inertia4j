package dev.arkoder.inertia4j.springshared;

import dev.arkoder.inertia4j.core.HttpSsrGateway;
import dev.arkoder.inertia4j.core.InertiaRenderer;
import dev.arkoder.inertia4j.core.PropertyNaming;
import dev.arkoder.inertia4j.core.SsrServerProcess;
import dev.arkoder.inertia4j.core.vite.ViteConfig;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for Inertia4j integration with Spring Boot.
 * Properties are prefixed with `inertia`.
 * <p>
 * Example `application.properties`:
 * <pre>
 * inertia.template-path=templates/my-app.html
 * inertia.encrypt-history=true
 * inertia.ssr.enabled=true
 * inertia.ssr.url=http://127.0.0.1:13714
 * inertia.property-naming=snake
 * inertia.vite.build-directory=static/build
 * </pre>
 */
@ConfigurationProperties(prefix = "inertia")
public class InertiaConfigurationProperties {
    /**
     * The classpath path to the main HTML template file used by the default {@link dev.arkoder.inertia4j.core.SimpleTemplateRenderer}.
     */
    private String templatePath = "templates/app.html";

    /**
     * Default value for the encryptHistory flag, determining whether browser history state should be encrypted.
     */
    private boolean encryptHistory = false;

    /**
     * Id of the element the client-side application is mounted on.
     */
    private String rootId = InertiaRenderer.DefaultRootId;

    /**
     * Whether page objects list the top-level keys of shared props in {@code sharedProps}.
     */
    private boolean exposeSharedPropKeys = true;

    /**
     * Naming strategy used when converting typed props objects, also applied to the objects inside props by the default
     * {@link dev.arkoder.inertia4j.spi.PageObjectSerializer} ({@code camel} or {@code snake}).
     */
    private PropertyNaming propertyNaming = PropertyNaming.Camel;

    /**
     * Server-side rendering settings.
     */
    private final Ssr ssr = new Ssr();

    /**
     * Vite integration settings.
     */
    private final ViteProperties vite = new ViteProperties();

    /**
     * Inertia filter settings.
     */
    private final FilterProperties filter = new FilterProperties();

    /**
     * Validation error settings.
     */
    private final ValidationProperties validation = new ValidationProperties();

    /**
     * @return the classpath path to the main HTML template file used by the default {@link
     * dev.arkoder.inertia4j.core.SimpleTemplateRenderer}.
     */
    public String getTemplatePath() {
        return templatePath;
    }

    /**
     * @param templatePath the classpath path to the main HTML template file used by the default {@link
     * dev.arkoder.inertia4j.core.SimpleTemplateRenderer}.
     */
    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    /**
     * @return default value for the encryptHistory flag, determining whether browser history state should be encrypted.
     */
    public boolean isEncryptHistory() {
        return encryptHistory;
    }

    /**
     * @param encryptHistory default value for the encryptHistory flag, determining whether browser history state should
     * be encrypted.
     */
    public void setEncryptHistory(boolean encryptHistory) {
        this.encryptHistory = encryptHistory;
    }

    /**
     * @return id of the element the client-side application is mounted on.
     */
    public String getRootId() {
        return rootId;
    }

    /**
     * @param rootId id of the element the client-side application is mounted on.
     */
    public void setRootId(String rootId) {
        this.rootId = rootId;
    }

    /**
     * @return whether page objects list the top-level keys of shared props in {@code sharedProps}.
     */
    public boolean isExposeSharedPropKeys() {
        return exposeSharedPropKeys;
    }

    /**
     * @param exposeSharedPropKeys whether page objects list the top-level keys of shared props in {@code sharedProps}.
     */
    public void setExposeSharedPropKeys(boolean exposeSharedPropKeys) {
        this.exposeSharedPropKeys = exposeSharedPropKeys;
    }

    /**
     * @return naming strategy used when converting typed props objects.
     */
    public PropertyNaming getPropertyNaming() {
        return propertyNaming;
    }

    /**
     * @param propertyNaming naming strategy used when converting typed props objects.
     */
    public void setPropertyNaming(PropertyNaming propertyNaming) {
        this.propertyNaming = propertyNaming;
    }

    /**
     * @return server-side rendering settings.
     */
    public Ssr getSsr() {
        return ssr;
    }

    /**
     * @return Inertia filter settings, prefixed with `inertia.filter`.
     */
    public FilterProperties getFilter() {
        return filter;
    }

    /**
     * @return validation error settings, prefixed with `inertia.validation`.
     */
    public ValidationProperties getValidation() {
        return validation;
    }

    /**
     * @return Vite integration settings, prefixed with `inertia.vite`.
     */
    public ViteProperties getVite() {
        return vite;
    }

    /**
     * Server-side rendering settings, prefixed with `inertia.ssr`.
     */
    public static class Ssr {
        /**
         * Whether full page loads are server-side rendered.
         */
        private boolean enabled = false;

        /**
         * URL of the server-side rendering server.
         */
        private String url = HttpSsrGateway.DefaultUrl;

        /**
         * URL of the Vite development server rendering pages while it runs, i.e. while its hot file exists.
         * Defaults to the URL written in the hot file. Always used when the Vite integration is disabled.
         */
        private String hotUrl;

        /**
         * Timeout of render requests.
         */
        private Duration timeout = HttpSsrGateway.DefaultTimeout;

        /**
         * Whether failed renders throw instead of falling back to client-side rendering.
         */
        private boolean throwOnError = false;

        /**
         * Request paths never server-side rendered; {@code *} matches any sequence of characters.
         */
        private List<String> except = new ArrayList<>();

        /**
         * Path of the server-side rendering bundle, such as {@code build/ssr/ssr.mjs}. While it is missing, pages are
         * rendered client-side without contacting the server, unless the Vite dev server renders them.
         */
        private @Nullable String bundle;

        /**
         * Whether pages are rendered client-side while the configured bundle is missing.
         */
        private boolean ensureBundleExists = true;

        /**
         * Whether a warning is logged on startup when the server-side rendering server is unreachable. Skipped while
         * the Vite dev server renders pages.
         */
        private boolean checkOnStartup = false;

        /**
         * Settings of the server-side rendering server run by the application.
         */
        private final Process process = new Process();

        /**
         * @return whether the Vite integration is enabled: placeholders, asset version and asset serving.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled whether the Vite integration is enabled: placeholders, asset version and asset serving.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return URL of the server-side rendering server.
         */
        public String getUrl() {
            return url;
        }

        /**
         * @param url URL of the server-side rendering server.
         */
        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * @return URL of the Vite development server rendering pages while it runs, i.e. while its hot file exists.
         * Defaults to the URL written in the hot file. Always used when the Vite integration is disabled.
         */
        public String getHotUrl() {
            return hotUrl;
        }

        /**
         * @param hotUrl URL of the Vite development server rendering pages while it runs, i.e. while its hot file
         * exists. Defaults to the URL written in the hot file. Always used when the Vite integration is disabled.
         */
        public void setHotUrl(String hotUrl) {
            this.hotUrl = hotUrl;
        }

        /**
         * @return timeout of render requests.
         */
        public Duration getTimeout() {
            return timeout;
        }

        /**
         * @param timeout timeout of render requests.
         */
        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        /**
         * @return whether failed renders throw instead of falling back to client-side rendering.
         */
        public boolean isThrowOnError() {
            return throwOnError;
        }

        /**
         * @param throwOnError whether failed renders throw instead of falling back to client-side rendering.
         */
        public void setThrowOnError(boolean throwOnError) {
            this.throwOnError = throwOnError;
        }

        /**
         * @return request paths never server-side rendered; {@code *} matches any sequence of characters.
         */
        public List<String> getExcept() {
            return except;
        }

        /**
         * @param except request paths never server-side rendered; {@code *} matches any sequence of characters.
         */
        public void setExcept(List<String> except) {
            this.except = except;
        }

        /**
         * @return path of the server-side rendering bundle, such as {@code build/ssr/ssr.mjs}. While it is missing,
         * pages are rendered client-side without contacting the server, unless the Vite dev server renders them.
         */
        public @Nullable String getBundle() {
            return bundle;
        }

        /**
         * @param bundle path of the server-side rendering bundle, such as {@code build/ssr/ssr.mjs}. While it is
         * missing, pages are rendered client-side without contacting the server, unless the Vite dev server renders
         * them.
         */
        public void setBundle(@Nullable String bundle) {
            this.bundle = bundle;
        }

        /**
         * @return whether pages are rendered client-side while the configured bundle is missing.
         */
        public boolean isEnsureBundleExists() {
            return ensureBundleExists;
        }

        /**
         * @param ensureBundleExists whether pages are rendered client-side while the configured bundle is missing.
         */
        public void setEnsureBundleExists(boolean ensureBundleExists) {
            this.ensureBundleExists = ensureBundleExists;
        }

        /**
         * @return whether a warning is logged on startup when the server-side rendering server is unreachable. Skipped
         * while the Vite dev server renders pages.
         */
        public boolean isCheckOnStartup() {
            return checkOnStartup;
        }

        /**
         * @param checkOnStartup whether a warning is logged on startup when the server-side rendering server is
         * unreachable. Skipped while the Vite dev server renders pages.
         */
        public void setCheckOnStartup(boolean checkOnStartup) {
            this.checkOnStartup = checkOnStartup;
        }

        /**
         * @return settings of the server-side rendering server run by the application, prefixed with
         * `inertia.ssr.process`.
         */
        public Process getProcess() {
            return process;
        }

        /**
         * Settings of the server-side rendering server run by the application, prefixed with `inertia.ssr.process`.
         */
        public static class Process {
            /**
             * Whether the application runs the bundle with the runtime on startup and stops it on shutdown. Not
             * started while the Vite dev server renders pages.
             */
            private boolean enabled = false;

            /**
             * Program running the bundle, such as {@code node}, {@code bun} or an absolute path.
             */
            private String runtime = SsrServerProcess.DefaultRuntime;

            /**
             * Arguments passed to the runtime before the bundle.
             */
            private List<String> arguments = new ArrayList<>();

            /**
             * Working directory of the process, the one of the application when unset.
             */
            private @Nullable String workingDirectory;

            /**
             * Environment variables added to the environment of the process.
             */
            private Map<String, String> environment = new HashMap<>();

            /**
             * Time given to the server to become healthy on startup.
             */
            private Duration startupTimeout = SsrServerProcess.DefaultStartupTimeout;

            /**
             * Time given to the server to exit after a shutdown request before its process is destroyed.
             */
            private Duration shutdownTimeout = SsrServerProcess.DefaultShutdownTimeout;

            /**
             * @return whether the Vite integration is enabled: placeholders, asset version and asset serving.
             */
            public boolean isEnabled() {
                return enabled;
            }

            /**
             * @param enabled whether the Vite integration is enabled: placeholders, asset version and asset serving.
             */
            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            /**
             * @return program running the bundle, such as {@code node}, {@code bun} or an absolute path.
             */
            public String getRuntime() {
                return runtime;
            }

            /**
             * @param runtime program running the bundle, such as {@code node}, {@code bun} or an absolute path.
             */
            public void setRuntime(String runtime) {
                this.runtime = runtime;
            }

            /**
             * @return arguments passed to the runtime before the bundle.
             */
            public List<String> getArguments() {
                return arguments;
            }

            /**
             * @param arguments arguments passed to the runtime before the bundle.
             */
            public void setArguments(List<String> arguments) {
                this.arguments = arguments;
            }

            /**
             * @return working directory of the process, the one of the application when unset.
             */
            public @Nullable String getWorkingDirectory() {
                return workingDirectory;
            }

            /**
             * @param workingDirectory working directory of the process, the one of the application when unset.
             */
            public void setWorkingDirectory(@Nullable String workingDirectory) {
                this.workingDirectory = workingDirectory;
            }

            /**
             * @return environment variables added to the environment of the process.
             */
            public Map<String, String> getEnvironment() {
                return environment;
            }

            /**
             * @param environment environment variables added to the environment of the process.
             */
            public void setEnvironment(Map<String, String> environment) {
                this.environment = environment;
            }

            /**
             * @return time given to the server to become healthy on startup.
             */
            public Duration getStartupTimeout() {
                return startupTimeout;
            }

            /**
             * @param startupTimeout time given to the server to become healthy on startup.
             */
            public void setStartupTimeout(Duration startupTimeout) {
                this.startupTimeout = startupTimeout;
            }

            /**
             * @return time given to the server to exit after a shutdown request before its process is destroyed.
             */
            public Duration getShutdownTimeout() {
                return shutdownTimeout;
            }

            /**
             * @param shutdownTimeout time given to the server to exit after a shutdown request before its process is
             * destroyed.
             */
            public void setShutdownTimeout(Duration shutdownTimeout) {
                this.shutdownTimeout = shutdownTimeout;
            }
        }
    }

    /**
     * Inertia filter settings, prefixed with `inertia.filter`.
     */
    public static class FilterProperties {
        /**
         * Whether the {@link InertiaFilter} is registered: asset version check before handlers, redirect back on empty
         * responses, 303 See Other after PUT, PATCH and DELETE requests, URL fragment redirects and
         * {@code Vary: X-Inertia} on every response.
         */
        private boolean enabled = true;

        /**
         * @return whether the Vite integration is enabled: placeholders, asset version and asset serving.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled whether the Vite integration is enabled: placeholders, asset version and asset serving.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Validation error settings, prefixed with `inertia.validation`.
     */
    public static class ValidationProperties {
        /**
         * Whether validation errors set from a Spring {@link org.springframework.validation.Errors} send every message
         * of each field, as a list, instead of the first one.
         */
        private boolean allErrors = false;

        /**
         * @return whether validation errors set from a Spring {@link org.springframework.validation.Errors} send every
         * message of each field, as a list, instead of the first one.
         */
        public boolean isAllErrors() {
            return allErrors;
        }

        /**
         * @param allErrors whether validation errors set from a Spring {@link org.springframework.validation.Errors}
         * send every message of each field, as a list, instead of the first one.
         */
        public void setAllErrors(boolean allErrors) {
            this.allErrors = allErrors;
        }
    }

    /**
     * Vite integration settings, prefixed with `inertia.vite`.
     */
    public static class ViteProperties {
        /**
         * Whether the Vite integration is enabled: placeholders, asset version and asset serving.
         */
        private boolean enabled = true;

        /**
         * File whose presence, written by the Vite dev server, enables dev mode.
         */
        private String hotFile = "vite.hot";

        /**
         * Classpath directory containing the Vite build output.
         */
        private String buildDirectory = "static/build";

        /**
         * Classpath location of the Vite manifest, `null` for `{build-directory}/.vite/manifest.json`.
         */
        private @Nullable String manifest;

        /**
         * URL prefix under which built files are served.
         */
        private String publicPath = "/build/";

        /**
         * `max-age` of the `Cache-Control` header sent with built files.
         */
        private Duration cacheMaxAge = Duration.ofDays(365);

        /**
         * Manifest field holding the Subresource Integrity hash of chunks, written by `vite-plugin-manifest-sri`;
         * empty or `false` to render tags without `integrity` attribute.
         */
        private String integrityKey = ViteConfig.DefaultIntegrityKey;

        /**
         * Request attribute holding the Content Security Policy nonce added to the rendered Vite tags; empty to
         * render tags without nonce.
         */
        private String nonceAttribute = "cspNonce";

        /**
         * @return whether the Vite integration is enabled: placeholders, asset version and asset serving.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled whether the Vite integration is enabled: placeholders, asset version and asset serving.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return file whose presence, written by the Vite dev server, enables dev mode.
         */
        public String getHotFile() {
            return hotFile;
        }

        /**
         * @param hotFile file whose presence, written by the Vite dev server, enables dev mode.
         */
        public void setHotFile(String hotFile) {
            this.hotFile = hotFile;
        }

        /**
         * @return classpath directory containing the Vite build output.
         */
        public String getBuildDirectory() {
            return buildDirectory;
        }

        /**
         * @param buildDirectory classpath directory containing the Vite build output.
         */
        public void setBuildDirectory(String buildDirectory) {
            this.buildDirectory = buildDirectory;
        }

        /**
         * @return classpath location of the Vite manifest, `null` for `{build-directory}/.vite/manifest.json`.
         */
        public @Nullable String getManifest() {
            return manifest;
        }

        /**
         * @param manifest classpath location of the Vite manifest, `null` for `{build-directory}/.vite/manifest.json`.
         */
        public void setManifest(@Nullable String manifest) {
            this.manifest = manifest;
        }

        /**
         * @return URL prefix under which built files are served.
         */
        public String getPublicPath() {
            return publicPath;
        }

        /**
         * @param publicPath URL prefix under which built files are served.
         */
        public void setPublicPath(String publicPath) {
            this.publicPath = publicPath;
        }

        /**
         * @return `max-age` of the `Cache-Control` header sent with built files.
         */
        public Duration getCacheMaxAge() {
            return cacheMaxAge;
        }

        /**
         * @param cacheMaxAge `max-age` of the `Cache-Control` header sent with built files.
         */
        public void setCacheMaxAge(Duration cacheMaxAge) {
            this.cacheMaxAge = cacheMaxAge;
        }

        /**
         * @return manifest field holding the Subresource Integrity hash of chunks, written by
         * `vite-plugin-manifest-sri`; empty or `false` to render tags without `integrity` attribute.
         */
        public String getIntegrityKey() {
            return integrityKey;
        }

        /**
         * @param integrityKey manifest field holding the Subresource Integrity hash of chunks, written by
         * `vite-plugin-manifest-sri`; empty or `false` to render tags without `integrity` attribute.
         */
        public void setIntegrityKey(String integrityKey) {
            this.integrityKey = integrityKey;
        }

        /**
         * @return request attribute holding the Content Security Policy nonce added to the rendered Vite tags; empty to
         * render tags without nonce.
         */
        public String getNonceAttribute() {
            return nonceAttribute;
        }

        /**
         * @param nonceAttribute request attribute holding the Content Security Policy nonce added to the rendered Vite
         * tags; empty to render tags without nonce.
         */
        public void setNonceAttribute(String nonceAttribute) {
            this.nonceAttribute = nonceAttribute;
        }

        ViteConfig toViteConfig() {
            ViteConfig.Builder builder = ViteConfig.builder()
                .hotFile(Path.of(hotFile))
                .buildDirectory(buildDirectory)
                .manifestPath(manifest)
                .publicPath(publicPath)
                .integrityKey("false".equalsIgnoreCase(integrityKey.trim()) ? null : integrityKey);

            if (!nonceAttribute.isBlank()) {
                String attribute = nonceAttribute.trim();
                builder.nonceProvider(() -> currentRequestAttribute(attribute));
            }

            return builder.build();
        }

        private static @Nullable String currentRequestAttribute(String name) {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                return null;
            }

            Object value = attributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);

            return value == null ? null : value.toString();
        }
    }
}
