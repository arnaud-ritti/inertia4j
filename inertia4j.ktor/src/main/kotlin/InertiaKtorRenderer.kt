package io.github.inertia4j.ktor

import io.github.inertia4j.core.HttpResponse
import io.github.inertia4j.core.InertiaProps
import io.github.inertia4j.core.InertiaRenderer
import io.github.inertia4j.core.InertiaRenderingOptions
import io.github.inertia4j.core.Precognition as CorePrecognition
import io.github.inertia4j.core.PropsExtractor
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.util.function.Supplier

/**
 * Ktor-specific renderer that integrates with the core [InertiaRenderer].
 * Provides an inner [Renderer] class accessible within Ktor routing handlers
 * to render Inertia responses or handle redirects.
 *
 * @property coreRenderer The underlying core Inertia renderer instance.
 * @property configuration The Ktor plugin configuration.
 */
class InertiaKtorRenderer internal constructor(
    private val coreRenderer: InertiaRenderer,
    private val configuration: InertiaKtorConfiguration,
) {
    private val flashStore = configuration.flashStoreOrDefault

    /**
     * Provides Inertia rendering methods within the context of a specific Ktor [RoutingCall].
     *
     * @property call The current Ktor routing call.
     */
    inner class Renderer internal constructor(private val call: RoutingCall) {
        private val request = InertiaKtorHttpRequest(call)

        /**
         * Renders an Inertia response for the specified component and props.
         * Flash data, validation errors and flags stored before a redirect are consumed, unless the response is
         * an asset version conflict, whose follow-up request receives them instead.
         *
         * @param name The name of the client-side component to render.
         * @param props Pairs of props passed to the component. Function values are resolved lazily.
         * @param url The URL for the page object. Defaults to the path and query string of the request.
         * @param encryptHistory Whether to encrypt history state. Defaults to the plugin configuration.
         * @param clearHistory Whether to clear history state. Defaults to `false`.
         * @param status HTTP status of the response. Defaults to 200 OK.
         */
        suspend fun render(
            name: String,
            vararg props: Pair<String, Any?>,
            url: String = request.url,
            encryptHistory: Boolean = configuration.encryptHistory,
            clearHistory: Boolean = false,
            status: HttpStatusCode = HttpStatusCode.OK
        ) {
            val stored = flashStore.read(call)

            @Suppress("UNCHECKED_CAST")
            val options = InertiaRenderingOptions.builder(name, url)
                .sharedProps(lazyProps(sharedProps()))
                .props(lazyProps(mapOf(*props)))
                .errors(stored[ErrorsKey] as? Map<String, Any?> ?: emptyMap<String, Any?>())
                .flash(stored[FlashDataKey] as? Map<String, Any?> ?: emptyMap<String, Any?>())
                .preserveFragment(stored[PreserveFragmentKey] == true)
                .clearHistory(clearHistory || stored[ClearHistoryKey] == true)
                .encryptHistory(encryptHistory)
                .status(status.value)
                .build()

            val response = coreRenderer.render(request, options)

            if (response.code != HttpStatusCode.Conflict.value && stored.isNotEmpty()) {
                flashStore.write(call, emptyMap())
            }

            respond(response)
        }

        /**
         * Renders the page described by a props object annotated with [io.github.inertia4j.annotations.InertiaPage],
         * using its component name and properties converted with [InertiaKtorConfiguration.propertyNaming].
         *
         * @param pageProps page props object.
         * @param url The URL for the page object. Defaults to the path and query string of the request.
         * @param encryptHistory Whether to encrypt history state. Defaults to the plugin configuration.
         * @param clearHistory Whether to clear history state. Defaults to `false`.
         * @param status HTTP status of the response. Defaults to 200 OK.
         * @throws IllegalArgumentException if the class is not annotated with `@InertiaPage`.
         */
        suspend fun render(
            pageProps: Any,
            url: String = request.url,
            encryptHistory: Boolean = configuration.encryptHistory,
            clearHistory: Boolean = false,
            status: HttpStatusCode = HttpStatusCode.OK
        ) {
            val props = PropsExtractor.toMap(pageProps, configuration.propertyNaming)

            render(
                PropsExtractor.componentName(pageProps),
                *props.toList().toTypedArray(),
                url = url,
                encryptHistory = encryptHistory,
                clearHistory = clearHistory,
                status = status
            )
        }

        /**
         * Shares a prop with the response rendered for the current call.
         *
         * @param key prop key; a dotted key (e.g. `auth.user`) sets a nested prop.
         * @param value prop value. Function values are resolved lazily.
         */
        fun share(key: String, value: Any?) {
            call.attributes.computeIfAbsent(sharedPropsKey) { linkedMapOf() }[key] = value
        }

        /**
         * Shares a prop resolved a single time and remembered by the client across pages.
         *
         * @param key prop key.
         * @param supplier provides the prop value when the client does not remember it.
         */
        fun shareOnce(key: String, supplier: () -> Any?) {
            share(key, InertiaProps.once(Supplier(supplier)))
        }

        /**
         * Flashes data to the next rendered page, typically after a redirect.
         *
         * @param key flash key.
         * @param value flash value.
         */
        suspend fun flash(key: String, value: Any?) {
            flash(mapOf(key to value))
        }

        /**
         * Flashes data to the next rendered page, typically after a redirect.
         *
         * @param data flash data.
         */
        suspend fun flash(data: Map<String, Any?>) {
            mergeStored(FlashDataKey, data)
        }

        /**
         * Sets validation errors sent with the next rendered page. Each value is a message or a list of messages.
         *
         * @param errors validation errors, by field.
         */
        suspend fun errors(errors: Map<String, Any?>) {
            mergeStored(ErrorsKey, errors)
        }

        /**
         * Makes the client preserve the URL fragment of the original request on the next rendered page.
         */
        suspend fun preserveFragment() {
            flashStore.write(call, flashStore.read(call) + (PreserveFragmentKey to true))
        }

        /**
         * Makes the client clear its encrypted history state on the next rendered page.
         */
        suspend fun clearHistory() {
            flashStore.write(call, flashStore.read(call) + (ClearHistoryKey to true))
        }

        /**
         * Creates an Inertia-compatible redirect response: 303 See Other after PUT, PATCH and DELETE requests,
         * 302 Found otherwise, or 409 Conflict with `X-Inertia-Redirect` when the location has a URL fragment.
         *
         * @param location The URL to redirect to.
         */
        suspend fun redirect(location: String) {
            respond(coreRenderer.redirect(request, location))
        }

        /**
         * Redirects back to the page the request was sent from, as told by the `Referer` header, or to `/`.
         */
        suspend fun back() {
            redirect(call.request.header(HttpHeaders.Referrer) ?: "/")
        }

        /**
         * Makes the client perform a full page visit to a URL, possibly external to the Inertia application.
         *
         * @param location The URL to visit.
         */
        suspend fun location(location: String) {
            respond(coreRenderer.location(request, location))
        }

        /**
         * Whether the call is a Precognition validation request, which must be validated and answered with
         * [precognition] without being executed.
         */
        val isPrecognitive: Boolean get() = CorePrecognition.isPrecognitive(request)

        /**
         * Responds to a Precognition validation request: 204 No Content when the validated fields have no errors,
         * 422 Unprocessable Entity with the errors otherwise.
         *
         * @param errors validation error messages, by field.
         */
        suspend fun precognition(errors: Map<String, List<String>>) {
            respond(CorePrecognition.respond(request, errors))
        }

        private suspend fun sharedProps(): Map<String, Any?> {
            val allProps = linkedMapOf<String, Any?>()
            configuration.sharedDataProviders.forEach { allProps.putAll(it(call)) }
            call.attributes.getOrNull(sharedPropsKey)?.let { allProps.putAll(it) }
            return allProps
        }

        private suspend fun mergeStored(key: String, values: Map<String, Any?>) {
            val stored = flashStore.read(call)

            @Suppress("UNCHECKED_CAST")
            val current = stored[key] as? Map<String, Any?> ?: emptyMap()
            flashStore.write(call, stored + (key to current + values))
        }

        private fun lazyProps(props: Map<String, Any?>): Map<String, Any?> =
            props.mapValues { (_, value) -> toLazyProp(value) }

        private fun toLazyProp(value: Any?): Any? =
            if (value is Function0<*>) Supplier { value() } else value

        private suspend fun respond(coreResponse: HttpResponse) {
            coreResponse.headers.forEach { (name: String, values: List<String>) ->
                if (name == HttpHeaders.ContentType) return@forEach
                values.forEach { call.response.header(name, it) }
            }

            val status = HttpStatusCode.fromValue(coreResponse.code)
            val contentType = coreResponse.headers[HttpHeaders.ContentType]?.firstOrNull()
            if (contentType == null) {
                call.respond(status, coreResponse.body ?: "")
                return
            }

            call.respondText(coreResponse.body ?: "", ContentType.parse(contentType), status)
        }
    }

    companion object {
        /**
         * Attribute key used to store the [InertiaKtorRenderer] instance in the application attributes.
         */
        val key = AttributeKey<InertiaKtorRenderer>("inertiaKtor")

        private val sharedPropsKey = AttributeKey<MutableMap<String, Any?>>("inertiaSharedProps")

        private const val FlashDataKey = "inertia.flash_data"
        private const val ErrorsKey = "inertia.errors"
        private const val PreserveFragmentKey = "inertia.preserve_fragment"
        private const val ClearHistoryKey = "inertia.clear_history"
    }
}

private val InertiaKtorHttpRequest.url: String get() = getUrl()
