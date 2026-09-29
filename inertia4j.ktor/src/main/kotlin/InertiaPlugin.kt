package io.github.inertia4j.ktor

import io.github.inertia4j.core.InertiaRenderer
import io.ktor.server.application.*

/**
 * The Inertia Ktor plugin. Install it in your application to enable Inertia responses.
 */
val Inertia = createApplicationPlugin(
    name = "Inertia",
    createConfiguration = ::InertiaKtorConfiguration
) {
    val builder = InertiaRenderer
        .builder(
            pluginConfig.serializerOrDefault,
            pluginConfig.versionProvider,
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
