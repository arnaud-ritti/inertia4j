package dev.arkoder.inertia4j.ktor

import dev.arkoder.inertia4j.spi.InertiaException

/**
 * Exception thrown when attempting to use the `inertia` extension property on [io.ktor.server.routing.RoutingContext]
 * before the [Inertia] plugin has been installed in the Ktor application.
 */
class PluginNotInstalledException : InertiaException("Inertia plugin was not installed")
