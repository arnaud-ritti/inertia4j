package io.github.inertia4j.ktor

import io.github.inertia4j.core.DefaultJsonReader
import io.github.inertia4j.core.DefaultPageObjectSerializer
import io.github.inertia4j.core.HttpSsrGateway
import io.github.inertia4j.core.InertiaRenderer
import io.github.inertia4j.core.PropertyNaming
import io.github.inertia4j.core.PropsExtractor
import io.github.inertia4j.core.SimpleTemplateRenderer
import io.github.inertia4j.core.SsrRenderFailure
import io.github.inertia4j.core.SsrServerProcess
import io.github.inertia4j.core.vite.Vite
import io.github.inertia4j.spi.JsonReader
import io.github.inertia4j.spi.PageObjectSerializer
import io.github.inertia4j.spi.SsrGateway
import io.github.inertia4j.spi.TemplateRenderer
import io.ktor.server.application.*
import java.nio.file.Path
import java.time.Duration
import java.util.function.Supplier

/**
 * Configuration class for the Inertia Ktor plugin.
 * Allows customization of asset versioning, serialization, template rendering, server-side rendering and history
 * behavior.
 */
class InertiaKtorConfiguration {
    /**
     * Provides the current asset version, compared against the `X-Inertia-Version` header of requests.
     * Defaults to `null`, in which case the Vite asset version is used: a hash of the Vite manifest in production,
     * `"dev"` while the Vite dev server runs, and `"1"` when neither is available.
     */
    var versionProvider: (() -> String)? = null

    /**
     * The serializer used to convert the [io.github.inertia4j.spi.PageObject] into a JSON string.
     * Defaults to `null`. If left `null`, [DefaultPageObjectSerializer] will be used with [propertyNaming],
     * which requires Jackson Databind on the classpath.
     */
    var serializer: PageObjectSerializer? = null

    /**
     * The renderer used to render the base HTML template for full page loads.
     * Defaults to `null`. If left `null`, [SimpleTemplateRenderer] will be used,
     * loading the template specified by [templatePath] and replacing its Vite placeholders.
     */
    var templateRenderer: TemplateRenderer? = null

    /**
     * The classpath path to the HTML template file used by [SimpleTemplateRenderer].
     * Defaults to "templates/app.html".
     */
    var templatePath: String = "templates/app.html"

    /**
     * Default value for the `encryptHistory` flag in page objects. Defaults to `false`.
     */
    var encryptHistory: Boolean = false

    /**
     * Id of the element the client-side application is mounted on. Defaults to "app".
     */
    var rootId: String = InertiaRenderer.DefaultRootId

    /**
     * Whether page objects list the top-level keys of shared props in `sharedProps`. Defaults to `true`.
     */
    var exposeSharedPropKeys: Boolean = true

    /**
     * Reports exceptions of rescued deferred props. Defaults to logging them.
     */
    var exceptionReporter: ((RuntimeException) -> Unit)? = null

    /**
     * Keeps flash data, validation errors and redirect flags until the next rendered page.
     * Defaults to [SessionsFlashStore], which needs the Ktor `Sessions` plugin, when `ktor-server-sessions` and
     * Jackson Databind are on the classpath; otherwise flash data cannot be stored.
     */
    var flashStore: InertiaFlashStore? = null

    /**
     * Naming strategy used when converting typed props objects. Defaults to [PropertyNaming.Camel].
     * Also applied to the objects inside props by the default serializer; a custom [serializer] must use the same
     * naming strategy.
     */
    var propertyNaming: PropertyNaming = PropertyNaming.Camel

    /**
     * Whether the plugin applies the Inertia protocol to every call, including those answered without rendering a
     * page: a `GET` Inertia request sent with an outdated asset version receives a 409 Conflict before reaching its
     * route, an empty 200 OK answering an Inertia request redirects back to the `Referer` (or `/`), a 302 Found
     * answering a PUT, PATCH or DELETE Inertia request becomes a 303 See Other, a redirect to a location with a URL
     * fragment becomes a 409 Conflict with `X-Inertia-Redirect`, and every response carries `Vary: X-Inertia`.
     * Defaults to `true`.
     */
    var middleware: Boolean = true

    internal val ssr = SsrConfiguration()

    internal val viteConfiguration = ViteKtorConfiguration()

    /**
     * Configures the Vite integration.
     *
     * @param configure changes applied to the default [ViteKtorConfiguration].
     */
    fun vite(configure: ViteKtorConfiguration.() -> Unit) {
        viteConfiguration.configure()
    }

    internal val viteInstance: Vite by lazy { Vite(viteConfiguration.toViteConfig()) }

    internal val versionProviderOrDefault: () -> String get() {
        return versionProvider ?: viteInstance::version
    }

    internal val sharedDataProviders = mutableListOf<suspend (ApplicationCall) -> Map<String, Any?>>()

    /**
     * Registers a provider of data shared with all responses.
     *
     * @param provider computes the shared props of a call.
     */
    fun share(provider: suspend (ApplicationCall) -> Map<String, Any?>) {
        sharedDataProviders.add(provider)
    }

    /**
     * Registers a provider of a typed object, typically a class annotated with
     * [io.github.inertia4j.annotations.InertiaShared], whose properties are shared with every Inertia response.
     *
     * @param provider returns the shared props object for the given call.
     */
    fun shareTyped(provider: suspend (ApplicationCall) -> Any) {
        sharedDataProviders.add { call -> PropsExtractor.toMap(provider(call), propertyNaming) }
    }

    /**
     * Configures server-side rendering.
     *
     * @param configure configuration block.
     */
    fun ssr(configure: SsrConfiguration.() -> Unit) {
        ssr.configure()
    }

    internal val templateRendererOrDefault: TemplateRenderer get() {
        return templateRenderer ?: SimpleTemplateRenderer(templatePath, viteInstance)
    }

    internal val serializerOrDefault: PageObjectSerializer get() {
        return serializer ?: DefaultPageObjectSerializer(propertyNaming)
    }

    internal val flashStoreOrDefault: InertiaFlashStore get() {
        return flashStore ?: defaultFlashStore()
    }

    /**
     * Server-side rendering configuration.
     */
    class SsrConfiguration {
        /**
         * Whether full page loads are server-side rendered. Defaults to `false`.
         */
        var enabled: Boolean = false

        /**
         * URL of the server-side rendering server.
         */
        var url: String = HttpSsrGateway.DefaultUrl

        /**
         * URL of the Vite development server rendering pages while it runs, i.e. while its hot file exists.
         * Defaults to the URL written in the hot file.
         */
        var hotUrl: String? = null

        /**
         * Timeout of render requests. No timeout is applied when `null`.
         */
        var timeout: Duration? = HttpSsrGateway.DefaultTimeout

        /**
         * Whether failed renders throw instead of falling back to client-side rendering.
         */
        var throwOnError: Boolean = false

        /**
         * Request paths never server-side rendered; `*` matches any sequence of characters.
         */
        var except: List<String> = emptyList()

        /**
         * Reader parsing the server responses. Defaults to [DefaultJsonReader].
         */
        var jsonReader: JsonReader? = null

        /**
         * Gateway rendering pages, replacing the default HTTP gateway built from the other settings.
         */
        var gateway: SsrGateway? = null

        /**
         * Path of the server-side rendering bundle, such as `build/ssr/ssr.mjs`. While it is missing, pages are
         * rendered client-side without contacting the server, unless the Vite dev server renders them.
         */
        var bundle: Path? = null

        /**
         * Whether pages are rendered client-side while the configured [bundle] is missing. Defaults to `true`.
         */
        var ensureBundleExists: Boolean = true

        /**
         * Whether a warning is logged when the application starts and the server-side rendering server is
         * unreachable. Skipped while the Vite dev server renders pages. Defaults to `false`.
         */
        var checkOnStartup: Boolean = false

        internal val processConfiguration = SsrProcessConfiguration()

        /**
         * Configures the server-side rendering server run by the application.
         *
         * @param configure changes applied to the default [SsrProcessConfiguration].
         */
        fun process(configure: SsrProcessConfiguration.() -> Unit) {
            processConfiguration.configure()
        }

        internal var failureListener: ((SsrRenderFailure) -> Unit)? = null

        /**
         * Registers a listener notified of failed renders, replacing the default one logging them as warnings.
         *
         * @param listener failure listener.
         */
        fun onFailure(listener: (SsrRenderFailure) -> Unit) {
            failureListener = listener
        }

        internal fun gatewayOrDefault(vite: Vite, defaultFailureListener: (SsrRenderFailure) -> Unit): SsrGateway? {
            if (!enabled) return null

            if (gateway != null) return gateway

            return httpGatewayBuilder(vite)
                .throwOnError(throwOnError)
                .onFailure(failureListener ?: defaultFailureListener)
                .build()
        }

        internal fun serverGateway(renderGateway: SsrGateway, vite: Vite): HttpSsrGateway {
            return renderGateway as? HttpSsrGateway ?: httpGatewayBuilder(vite).build()
        }

        internal fun serverProcessOrNull(serverGateway: HttpSsrGateway): SsrServerProcess? {
            if (!processConfiguration.enabled) return null

            val bundlePath = checkNotNull(bundle) { "ssr.process.enabled requires ssr.bundle to be set" }

            return processConfiguration.toProcess(serverGateway, bundlePath)
        }

        private fun httpGatewayBuilder(vite: Vite): HttpSsrGateway.Builder {
            val builder = HttpSsrGateway.builder()
            val fixedHotUrl = hotUrl

            if (fixedHotUrl != null) {
                builder.hotUrl(Supplier { if (vite.isDevMode) fixedHotUrl else null })
            } else {
                builder.hotUrl(Supplier { vite.devServerUrlIfRunning() })
            }

            return builder
                .url(url)
                .timeout(timeout)
                .jsonReader(jsonReader ?: DefaultJsonReader())
                .bundle(bundle)
                .ensureBundleExists(ensureBundleExists)
        }
    }

    /**
     * Settings of the server-side rendering server run by the application: started with
     * `<runtime> [arguments...] <bundle>` when the application starts, unless the Vite dev server renders pages, and
     * stopped when it stops.
     */
    class SsrProcessConfiguration {
        /**
         * Whether the application runs the server-side rendering bundle. Requires [SsrConfiguration.bundle].
         * Defaults to `false`.
         */
        var enabled: Boolean = false

        /**
         * Program running the bundle, such as `node`, `bun` or an absolute path. Defaults to `node`.
         */
        var runtime: String = SsrServerProcess.DefaultRuntime

        /**
         * Arguments passed to the runtime before the bundle.
         */
        var arguments: List<String> = emptyList()

        /**
         * Working directory of the process, the one of the application when `null`.
         */
        var workingDirectory: Path? = null

        /**
         * Environment variables added to the environment of the process.
         */
        var environment: Map<String, String> = emptyMap()

        /**
         * Time given to the server to become healthy when the application starts.
         */
        var startupTimeout: Duration = SsrServerProcess.DefaultStartupTimeout

        /**
         * Time given to the server to exit after a shutdown request before its process is destroyed.
         */
        var shutdownTimeout: Duration = SsrServerProcess.DefaultShutdownTimeout

        internal fun toProcess(gateway: HttpSsrGateway, bundle: Path): SsrServerProcess {
            return SsrServerProcess.builder(gateway, bundle)
                .runtime(runtime)
                .arguments(arguments)
                .workingDirectory(workingDirectory)
                .environment(environment)
                .startupTimeout(startupTimeout)
                .shutdownTimeout(shutdownTimeout)
                .build()
        }
    }
}
