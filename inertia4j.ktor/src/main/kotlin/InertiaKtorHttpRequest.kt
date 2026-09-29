package dev.arkoder.inertia4j.ktor

import dev.arkoder.inertia4j.core.HttpRequest
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*

/**
 * Implementation of [HttpRequest] that wraps a Ktor [ApplicationCall].
 * This acts as an adapter between the Ktor request object and the core Inertia4J renderer.
 */
internal class InertiaKtorHttpRequest(private val call: ApplicationCall) : HttpRequest {
    override fun getHeader(name: String): String? = call.request.header(name)

    override fun getMethod(): String = call.request.httpMethod.value

    override fun getUrl(): String {
        val uri = call.request.uri

        return if (uri.startsWith("/")) uri else "/$uri"
    }

    override fun getFullUrl(): String {
        val origin = call.request.origin
        val defaultPort = (origin.scheme == "http" && origin.serverPort == 80) ||
            (origin.scheme == "https" && origin.serverPort == 443)
        val port = if (defaultPort) "" else ":${origin.serverPort}"

        return "${origin.scheme}://${origin.serverHost}$port${getUrl()}"
    }
}
