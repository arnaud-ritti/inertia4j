package dev.arkoder.inertia4j.ktor

import io.ktor.server.application.*

/**
 * Keeps data carried across a redirect, such as flash data and validation errors, until the next response
 * rendering a page consumes it.
 */
interface InertiaFlashStore {
    /**
     * Reads the stored data.
     *
     * @param call current call.
     * @return stored values by key, empty when nothing is stored.
     */
    suspend fun read(call: ApplicationCall): Map<String, Any?>

    /**
     * Replaces the stored data.
     *
     * @param call current call.
     * @param data values by key; an empty map clears the store.
     */
    suspend fun write(call: ApplicationCall, data: Map<String, Any?>)
}
