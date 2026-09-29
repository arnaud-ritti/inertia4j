package io.github.inertia4j.ktor

import io.github.inertia4j.core.InertiaRenderer
import io.ktor.http.*
import io.ktor.server.application.*
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

    pluginConfig.ssr.gatewayOrDefault()?.let { builder.ssrGateway(it) }

    application.attributes.put(
        InertiaKtorRenderer.key,
        InertiaKtorRenderer(builder.build(), pluginConfig)
    )

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
