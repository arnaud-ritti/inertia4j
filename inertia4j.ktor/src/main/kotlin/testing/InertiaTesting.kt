package io.github.inertia4j.ktor.testing

import io.github.inertia4j.core.DefaultJsonReader
import io.github.inertia4j.core.InertiaHeaders
import io.github.inertia4j.core.testing.AssertableInertia
import io.github.inertia4j.core.testing.InertiaReloader
import io.github.inertia4j.core.testing.ReloadRequest
import io.github.inertia4j.spi.JsonReader
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.runBlocking

/**
 * Asserts that the response is an Inertia page and runs [assertions] on it:
 *
 * ```kotlin
 * client.get("/users").assertInertia {
 *     component("Users/Index")
 *     has("users", 3) { user -> user.where("name", "Jane") }
 *     loadDeferredProps { deferred -> deferred.has("permissions") }
 * }
 * ```
 *
 * It accepts both Inertia requests, answered with the page object JSON, and full visits, answered with the HTML
 * document holding it. Reload assertions ([AssertableInertia.reloadOnly], [AssertableInertia.reloadExcept],
 * [AssertableInertia.loadDeferredProps]) send requests with the client of this response, carrying over the cookies
 * of the original request and response unless the client manages them with [HttpCookies].
 *
 * @param jsonReader parser of the page object, [DefaultJsonReader] by default.
 * @return the page, for further assertions.
 */
suspend fun HttpResponse.assertInertia(
    jsonReader: JsonReader = DefaultJsonReader(),
    assertions: AssertableInertia.() -> Unit = {},
): AssertableInertia {
    val page = inertiaPage(jsonReader)
    page.assertions()

    return page
}

/**
 * Gets the Inertia page of the response, for instance to read its props.
 *
 * @param jsonReader parser of the page object, [DefaultJsonReader] by default.
 * @throws AssertionError if the response is not an Inertia page.
 */
suspend fun HttpResponse.inertiaPage(jsonReader: JsonReader = DefaultJsonReader()): AssertableInertia =
    AssertableInertia.fromResponseBody(pageBody(), jsonReader, KtorClientReloader(this))

private suspend fun HttpResponse.pageBody(): String {
    val inertiaLocation = headers[InertiaHeaders.Location]

    if (status == HttpStatusCode.Conflict && inertiaLocation != null) {
        throw AssertionError("Not a valid Inertia response: 409 Conflict with ${InertiaHeaders.Location} $inertiaLocation")
    }

    if (status.value in 300..399) {
        throw AssertionError("Not a valid Inertia response: redirect ${status.value} to ${headers[HttpHeaders.Location]}")
    }

    return bodyAsText()
}

/**
 * Reloads pages with the client of the original response. [InertiaReloader] is synchronous, so the request runs in
 * [runBlocking]; the test engine handles it on its own threads.
 */
private class KtorClientReloader(private val original: HttpResponse) : InertiaReloader {
    override fun reload(request: ReloadRequest): String = runBlocking {
        val client = original.call.client
        val originalUrl = original.request.url
        val reloadUrl = URLBuilder(protocol = originalUrl.protocol, host = originalUrl.host, port = originalUrl.port)
            .takeFrom(request.url)
            .build()
        val cookies = if (client.pluginOrNull(HttpCookies) == null) originalCookies() else null

        client.get {
            url(reloadUrl)
            request.headers.forEach { (name, value) -> header(name, value) }

            if (!cookies.isNullOrEmpty()) {
                header(HttpHeaders.Cookie, cookies)
            }
        }.pageBody()
    }

    private fun originalCookies(): String {
        val cookies = linkedMapOf<String, String>()

        original.request.headers.getAll(HttpHeaders.Cookie).orEmpty()
            .flatMap { it.split(';') }
            .forEach { addCookie(cookies, it) }

        original.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            .forEach { addCookie(cookies, it.substringBefore(';')) }

        return cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
    }

    private fun addCookie(cookies: MutableMap<String, String>, pair: String) {
        val name = pair.substringBefore('=').trim()

        if (name.isEmpty() || '=' !in pair) {
            return
        }

        cookies[name] = pair.substringAfter('=').trim()
    }
}
