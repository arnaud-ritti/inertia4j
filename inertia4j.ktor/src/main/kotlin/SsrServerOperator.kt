package dev.arkoder.inertia4j.ktor

import dev.arkoder.inertia4j.core.HttpSsrGateway
import dev.arkoder.inertia4j.core.SsrServerProcess

/**
 * Starts the server-side rendering server and checks that it is reachable when the application starts, unless the
 * Vite dev server renders pages, and stops it when the application stops.
 */
internal class SsrServerOperator(
    private val gateway: HttpSsrGateway,
    val process: SsrServerProcess?,
    private val checkOnStartup: Boolean,
    private val warn: (String, Throwable?) -> Unit,
) {
    fun start() {
        if (gateway.hotUrl != null) return

        try {
            process?.start()
        } catch (exception: IllegalStateException) {
            warn("Unable to start the Inertia SSR server: ${exception.message}", exception.cause)
        }

        if (checkOnStartup && !gateway.isHealthy()) {
            warn("Inertia SSR server is not reachable at ${gateway.url}, pages are rendered client-side", null)
        }
    }

    fun stop() {
        process?.stop()
    }
}
