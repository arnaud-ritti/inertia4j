package dev.arkoder.inertia4j.ktor

import dev.arkoder.inertia4j.core.InertiaHeaders
import dev.arkoder.inertia4j.core.InertiaRedirects
import dev.arkoder.inertia4j.core.InertiaRenderer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

/**
 * The Inertia Ktor plugin. Install it in your application to enable Inertia responses.
 * It also serves the Vite build output unless disabled.
 */
val Inertia = createApplicationPlugin(
    name = "Inertia",
    createConfiguration = ::InertiaKtorConfiguration
) {
    val builder = InertiaRenderer
        .builder(
            pluginConfig.serializerOrDefault,
            pluginConfig.versionProviderOrDefault,
            pluginConfig.templateRendererOrDefault
        )
        .rootId(pluginConfig.rootId)
        .exposeSharedPropKeys(pluginConfig.exposeSharedPropKeys)
        .withoutSsr(*pluginConfig.ssr.except.toTypedArray())
        .exceptionReporter(
            pluginConfig.exceptionReporter
                ?: { exception -> application.log.error("Rescued deferred prop failed to resolve", exception) }
        )

    val ssrGateway = pluginConfig.ssr
        .gatewayOrDefault(pluginConfig.viteInstance) { failure -> application.log.warn(failure.toString()) }

    if (ssrGateway != null) {
        builder.ssrGateway(ssrGateway)

        val serverGateway = pluginConfig.ssr.serverGateway(ssrGateway, pluginConfig.viteInstance)
        val ssrServer = SsrServerOperator(
            serverGateway,
            pluginConfig.ssr.serverProcessOrNull(serverGateway),
            pluginConfig.ssr.checkOnStartup
        ) { message, cause -> application.log.warn(message, cause) }

        application.monitor.subscribe(ApplicationStarted) { ssrServer.start() }
        application.monitor.subscribe(ApplicationStopping) { ssrServer.stop() }
    }

    val coreRenderer = builder.build()

    application.attributes.put(
        InertiaKtorRenderer.key,
        InertiaKtorRenderer(coreRenderer, pluginConfig)
    )

    if (pluginConfig.middleware) {
        application.intercept(ApplicationCallPipeline.Plugins) {
            val versionConflict = coreRenderer.checkVersion(InertiaKtorHttpRequest(call)).orElse(null)
                ?: return@intercept

            versionConflict.headers.forEach { (name, values) -> values.forEach { call.response.header(name, it) } }
            call.respond(HttpStatusCode.fromValue(versionConflict.code))
            finish()
        }

        on(ResponseBodyReadyForSend) { call, content ->
            addVaryInertia(call, content)

            val request = InertiaKtorHttpRequest(call)
            if (!InertiaHeaders.isInertia(request)) return@on

            val status = content.status ?: call.response.status() ?: HttpStatusCode.OK

            if (isEmptyResponse(call, content, status)) {
                val redirectStatus = HttpStatusCode.fromValue(InertiaRedirects.status(request, HttpStatusCode.Found.value))
                call.response.header(HttpHeaders.Location, call.request.header(HttpHeaders.Referrer) ?: "/")
                call.response.status(redirectStatus)
                transformBodyTo(object : OutgoingContent.NoContent() {
                    override val status: HttpStatusCode = redirectStatus
                    override val headers: Headers = content.headers
                })
                return@on
            }

            if (status.value !in 300..399) return@on

            val location = call.response.headers[HttpHeaders.Location]
            val redirectStatus = if (location != null && InertiaRedirects.needsFragmentVisit(request, location)) {
                call.response.header(InertiaHeaders.Redirect, location)
                HttpStatusCode.Conflict
            } else {
                HttpStatusCode.fromValue(InertiaRedirects.status(request, status.value))
            }

            if (redirectStatus == status) return@on

            call.response.status(redirectStatus)

            if (content.status != null) {
                transformBodyTo(content.withStatus(redirectStatus))
            }
        }
    }

    val viteConfiguration = pluginConfig.viteConfiguration

    if (viteConfiguration.serveAssets) {
        val viteConfig = pluginConfig.viteInstance.config
        val cacheControl = "public, max-age=${viteConfiguration.cacheMaxAge.inWholeSeconds}, immutable"

        application.routing {
            staticResources(viteConfig.publicPath.removeSuffix("/"), viteConfig.buildDirectory) {
                modify { _, call -> call.response.header(HttpHeaders.CacheControl, cacheControl) }
            }
        }
    }
}

private fun addVaryInertia(call: ApplicationCall, content: OutgoingContent) {
    val varied = (call.response.headers.values(HttpHeaders.Vary) + content.headers.getAll(HttpHeaders.Vary).orEmpty())
        .flatMap { it.split(",") }
        .map { it.trim() }

    if (varied.none { it.equals(InertiaHeaders.Inertia, ignoreCase = true) }) {
        call.response.headers.append(HttpHeaders.Vary, InertiaHeaders.Inertia)
    }
}

private fun isEmptyResponse(call: ApplicationCall, content: OutgoingContent, status: HttpStatusCode): Boolean {
    if (status != HttpStatusCode.OK) return false

    if (call.request.httpMethod == HttpMethod.Head) return false

    return when (content) {
        is OutgoingContent.NoContent -> true
        is OutgoingContent.ByteArrayContent -> content.bytes().isEmpty()
        else -> content.contentLength == 0L
    }
}

private fun OutgoingContent.withStatus(status: HttpStatusCode): OutgoingContent {
    val original = this

    return when (original) {
        is OutgoingContent.NoContent -> object : OutgoingContent.NoContent() {
            override val status: HttpStatusCode = status
            override val headers: Headers = original.headers
        }

        is OutgoingContent.ByteArrayContent -> object : OutgoingContent.ByteArrayContent() {
            override val status: HttpStatusCode = status
            override val headers: Headers = original.headers
            override val contentType: ContentType? = original.contentType
            override val contentLength: Long? = original.contentLength
            override fun bytes(): ByteArray = original.bytes()
        }

        is OutgoingContent.WriteChannelContent -> object : OutgoingContent.WriteChannelContent() {
            override val status: HttpStatusCode = status
            override val headers: Headers = original.headers
            override val contentType: ContentType? = original.contentType
            override val contentLength: Long? = original.contentLength
            override suspend fun writeTo(channel: ByteWriteChannel) = original.writeTo(channel)
        }

        is OutgoingContent.ReadChannelContent -> object : OutgoingContent.ReadChannelContent() {
            override val status: HttpStatusCode = status
            override val headers: Headers = original.headers
            override val contentType: ContentType? = original.contentType
            override val contentLength: Long? = original.contentLength
            override fun readFrom(): ByteReadChannel = original.readFrom()
        }

        is OutgoingContent.ContentWrapper -> original.copy(original.delegate().withStatus(status))

        is OutgoingContent.ProtocolUpgrade -> original
    }
}

/**
 * Route-scoped plugin adding `Vary: Precognition` to every response of the routes handling Precognition
 * validation requests, so caches keep them apart from regular responses.
 */
val Precognition = createRouteScopedPlugin(name = "InertiaPrecognition") {
    onCall { call ->
        call.response.headers.append("Vary", "Precognition")
    }
}
