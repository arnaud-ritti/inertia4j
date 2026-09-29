package io.github.inertia4j.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.inertia4j.spi.InertiaException
import io.ktor.server.application.*
import io.ktor.server.sessions.*

/**
 * Session holding the data of [SessionsFlashStore], serialized as JSON.
 * Register it with the Ktor `Sessions` plugin using [InertiaSession.Serializer]:
 * ```
 * install(Sessions) {
 *     cookie<InertiaSession>("INERTIA_SESSION", SessionStorageMemory()) {
 *         serializer = InertiaSession.Serializer
 *     }
 * }
 * ```
 */
class InertiaSession(val json: String) {
    /**
     * Serializer storing the session as its JSON content.
     */
    object Serializer : SessionSerializer<InertiaSession> {
        override fun serialize(session: InertiaSession): String = session.json

        override fun deserialize(text: String): InertiaSession = InertiaSession(text)
    }
}

/**
 * [InertiaFlashStore] keeping data in an [InertiaSession] of the Ktor `Sessions` plugin.
 * Without the plugin or the session registration, nothing is ever stored and writing fails.
 */
class SessionsFlashStore : InertiaFlashStore {
    private val objectMapper = ObjectMapper()

    override suspend fun read(call: ApplicationCall): Map<String, Any?> {
        val session = try {
            call.sessions.get<InertiaSession>()
        } catch (exception: IllegalStateException) {
            null
        } catch (exception: MissingApplicationPluginException) {
            null
        } ?: return emptyMap()

        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(session.json, Map::class.java) as Map<String, Any?>
    }

    override suspend fun write(call: ApplicationCall, data: Map<String, Any?>) {
        try {
            if (data.isEmpty()) {
                call.sessions.clear<InertiaSession>()
                return
            }
            call.sessions.set(InertiaSession(objectMapper.writeValueAsString(data)))
        } catch (exception: IllegalStateException) {
            throw missingSessionException(exception)
        } catch (exception: MissingApplicationPluginException) {
            throw missingSessionException(exception)
        }
    }

    private fun missingSessionException(cause: Exception) = InertiaException(
        "Flash data, errors and redirect flags need the Sessions plugin with an InertiaSession registered, " +
            "or a custom InertiaFlashStore",
        cause
    )
}
