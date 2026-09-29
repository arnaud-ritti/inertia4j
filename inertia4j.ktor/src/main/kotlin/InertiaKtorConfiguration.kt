package io.github.inertia4j.ktor

import io.github.inertia4j.core.DefaultJsonReader
import io.github.inertia4j.core.DefaultPageObjectSerializer
import io.github.inertia4j.core.HttpSsrGateway
import io.github.inertia4j.core.InertiaRenderer
import io.github.inertia4j.core.PropertyNaming
import io.github.inertia4j.core.PropsExtractor
import io.github.inertia4j.core.SimpleTemplateRenderer
import io.github.inertia4j.core.SsrRenderFailure
import io.github.inertia4j.spi.JsonReader
import io.github.inertia4j.spi.PageObjectSerializer
import io.github.inertia4j.spi.SsrGateway
import io.github.inertia4j.spi.TemplateRenderer
import io.ktor.server.application.*
import java.time.Duration

/**
 * Configuration class for the Inertia Ktor plugin.
 * Allows customization of asset versioning, serialization, template rendering, server-side rendering and history
 * behavior.
 */
class InertiaKtorConfiguration {
    /**
     * Provides the current asset version. Defaults to returning "1".
     * This is used to compare against the `X-Inertia-Version` header in requests.
     */
    var versionProvider: () -> String = { "1" }

    /**
     * The serializer used to convert the [io.github.inertia4j.spi.PageObject] into a JSON string.
     * Defaults to `null`. If left `null`, [DefaultPageObjectSerializer] will be used with [propertyNaming],
     * which requires Jackson Databind on the classpath.
     */
    var serializer: PageObjectSerializer? = null

    /**
     * The renderer used to render the base HTML template for full page loads.
     * Defaults to `null`. If left `null`, [SimpleTemplateRenderer] will be used,
     * loading the template specified by [templatePath].
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
     * Defaults to [SessionsFlashStore], which needs the Ktor `Sessions` plugin.
     */
    var flashStore: InertiaFlashStore? = null

    /**
     * Naming strategy used when converting typed props objects. Defaults to [PropertyNaming.Camel].
     * Also applied to the objects inside props by the default serializer; a custom [serializer] must use the same
     * naming strategy.
     */
    var propertyNaming: PropertyNaming = PropertyNaming.Camel

    internal val ssr = SsrConfiguration()

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
        return templateRenderer ?: SimpleTemplateRenderer(templatePath)
    }

    internal val serializerOrDefault: PageObjectSerializer get() {
        return serializer ?: DefaultPageObjectSerializer(propertyNaming)
    }

    internal val flashStoreOrDefault: InertiaFlashStore get() {
        return flashStore ?: SessionsFlashStore()
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
         * URL of the Vite development server, used instead of the server-side rendering server when set.
         */
        var hotUrl: String? = null

        /**
         * Timeout of render requests. No timeout is applied when `null`.
         */
        var timeout: Duration? = null

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

        internal var failureListener: (SsrRenderFailure) -> Unit = {}

        /**
         * Registers a listener notified of failed renders.
         *
         * @param listener failure listener.
         */
        fun onFailure(listener: (SsrRenderFailure) -> Unit) {
            failureListener = listener
        }

        internal fun gatewayOrDefault(): SsrGateway? {
            if (!enabled) return null

            return gateway ?: HttpSsrGateway.builder()
                .url(url)
                .hotUrl(hotUrl)
                .timeout(timeout)
                .throwOnError(throwOnError)
                .jsonReader(jsonReader ?: DefaultJsonReader())
                .onFailure(failureListener)
                .build()
        }
    }
}
