package io.github.inertia4j.ktor

import io.github.inertia4j.core.InertiaRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * The main Ktor Application Plugin for integrating Inertia4J.
 * This plugin initializes the core [InertiaRenderer] based on the provided [InertiaKtorConfiguration],
 * makes the Ktor-specific [InertiaKtorRenderer] available via application attributes,
 * and serves the Vite build output unless disabled.
 */
val Inertia = createApplicationPlugin(
    name = "Inertia",
    createConfiguration = ::InertiaKtorConfiguration
) {
    val coreRenderer = InertiaRenderer(
        pluginConfig.serializerOrDefault,
        pluginConfig.versionProviderOrDefault,
        pluginConfig.templateRendererOrDefault
    )
    application.attributes.put(
        InertiaKtorRenderer.key,
        InertiaKtorRenderer(coreRenderer, pluginConfig)
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
