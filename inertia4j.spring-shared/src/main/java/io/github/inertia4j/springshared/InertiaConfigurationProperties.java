package io.github.inertia4j.springshared;

import io.github.inertia4j.core.HttpSsrGateway;
import io.github.inertia4j.core.InertiaRenderer;
import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.core.SsrServerProcess;
import io.github.inertia4j.core.vite.ViteConfig;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
     * The classpath path to the main HTML template file used by the default {@link io.github.inertia4j.core.SimpleTemplateRenderer}.
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
     * {@link io.github.inertia4j.spi.PageObjectSerializer} ({@code camel} or {@code snake}).
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

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public boolean isEncryptHistory() {
        return encryptHistory;
    }

    public void setEncryptHistory(boolean encryptHistory) {
        this.encryptHistory = encryptHistory;
    }

    public String getRootId() {
        return rootId;
    }

    public void setRootId(String rootId) {
        this.rootId = rootId;
    }

    public boolean isExposeSharedPropKeys() {
        return exposeSharedPropKeys;
    }

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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getHotUrl() {
            return hotUrl;
        }

        public void setHotUrl(String hotUrl) {
            this.hotUrl = hotUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public boolean isThrowOnError() {
            return throwOnError;
        }

        public void setThrowOnError(boolean throwOnError) {
            this.throwOnError = throwOnError;
        }

        public List<String> getExcept() {
            return except;
        }

        public void setExcept(List<String> except) {
            this.except = except;
        }

        public @Nullable String getBundle() {
            return bundle;
        }

        public void setBundle(@Nullable String bundle) {
            this.bundle = bundle;
        }

        public boolean isEnsureBundleExists() {
            return ensureBundleExists;
        }

        public void setEnsureBundleExists(boolean ensureBundleExists) {
            this.ensureBundleExists = ensureBundleExists;
        }

        public boolean isCheckOnStartup() {
            return checkOnStartup;
        }

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

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getRuntime() {
                return runtime;
            }

            public void setRuntime(String runtime) {
                this.runtime = runtime;
            }

            public List<String> getArguments() {
                return arguments;
            }

            public void setArguments(List<String> arguments) {
                this.arguments = arguments;
            }

            public @Nullable String getWorkingDirectory() {
                return workingDirectory;
            }

            public void setWorkingDirectory(@Nullable String workingDirectory) {
                this.workingDirectory = workingDirectory;
            }

            public Map<String, String> getEnvironment() {
                return environment;
            }

            public void setEnvironment(Map<String, String> environment) {
                this.environment = environment;
            }

            public Duration getStartupTimeout() {
                return startupTimeout;
            }

            public void setStartupTimeout(Duration startupTimeout) {
                this.startupTimeout = startupTimeout;
            }

            public Duration getShutdownTimeout() {
                return shutdownTimeout;
            }

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
         * Whether the {@link InertiaFilter} is registered: asset version check before handlers, 303 See Other after
         * PUT, PATCH and DELETE requests, and URL fragment redirects.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHotFile() {
            return hotFile;
        }

        public void setHotFile(String hotFile) {
            this.hotFile = hotFile;
        }

        public String getBuildDirectory() {
            return buildDirectory;
        }

        public void setBuildDirectory(String buildDirectory) {
            this.buildDirectory = buildDirectory;
        }

        public @Nullable String getManifest() {
            return manifest;
        }

        public void setManifest(@Nullable String manifest) {
            this.manifest = manifest;
        }

        public String getPublicPath() {
            return publicPath;
        }

        public void setPublicPath(String publicPath) {
            this.publicPath = publicPath;
        }

        public Duration getCacheMaxAge() {
            return cacheMaxAge;
        }

        public void setCacheMaxAge(Duration cacheMaxAge) {
            this.cacheMaxAge = cacheMaxAge;
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
