package dev.arkoder.inertia4j.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import dev.arkoder.inertia4j.spi.InertiaException
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
    private companion object {
        val objectMapper = ObjectMapper()
    }

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
        cause,
    )
}

/**
 * [InertiaFlashStore] used when Ktor Sessions or Jackson Databind is missing from the classpath:
 * nothing is ever stored, and writing fails with an explanation.
 */
internal object UnavailableFlashStore : InertiaFlashStore {
    override suspend fun read(call: ApplicationCall): Map<String, Any?> = emptyMap()

    override suspend fun write(call: ApplicationCall, data: Map<String, Any?>) {
        if (data.isEmpty()) return

        throw InertiaException(
            "Flash data, errors and redirect flags need io.ktor:ktor-server-sessions and " +
                "com.fasterxml.jackson.core:jackson-databind on the classpath, or a custom InertiaFlashStore",
        )
    }
}

internal fun defaultFlashStore(): InertiaFlashStore {
    val dependenciesPresent = listOf(
        "io.ktor.server.sessions.SessionsConfig",
        "com.fasterxml.jackson.databind.ObjectMapper",
    ).all(::isClassPresent)

    return if (dependenciesPresent) SessionsFlashStore() else UnavailableFlashStore
}

private fun isClassPresent(className: String): Boolean = try {
    Class.forName(className, false, InertiaFlashStore::class.java.classLoader)
    true
} catch (exception: ClassNotFoundException) {
    false
}
