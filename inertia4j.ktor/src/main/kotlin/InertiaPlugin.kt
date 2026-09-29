package io.github.inertia4j.ktor

import io.github.inertia4j.core.InertiaHeaders
import io.github.inertia4j.core.InertiaRedirects
import io.github.inertia4j.core.InertiaRenderer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
            val request = InertiaKtorHttpRequest(call)
            if (!InertiaHeaders.isInertia(request)) return@on

            val status = content.status ?: call.response.status() ?: return@on
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

            if (content is OutgoingContent.NoContent && content.status != null) {
                transformBodyTo(object : OutgoingContent.NoContent() {
                    override val status: HttpStatusCode = redirectStatus
                    override val headers: Headers = content.headers
                })
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

/**
 * Route-scoped plugin adding `Vary: Precognition` to every response of the routes handling Precognition
 * validation requests, so caches keep them apart from regular responses.
 */
val Precognition = createRouteScopedPlugin(name = "InertiaPrecognition") {
    onCall { call ->
        call.response.headers.append("Vary", "Precognition")
    }
}
