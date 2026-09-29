@file:Suppress("ktlint:standard:max-line-length") // expected JSON responses are kept on one line

package dev.arkoder.inertia4j.ktor

import dev.arkoder.inertia4j.annotations.InertiaFlash
import dev.arkoder.inertia4j.core.InertiaProps
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InertiaKtorTest {
    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(Sessions) {
                cookie<InertiaSession>("INERTIA_SESSION", SessionStorageMemory()) {
                    serializer = InertiaSession.Serializer
                }
            }
            install(Inertia) {
                encryptHistory = true
                versionProvider = { "1" }
            }
        }
        block()
    }

    private fun HttpRequestBuilder.inertia() {
        header("X-Inertia", "true")
        header("X-Inertia-Version", "1")
    }

    @Test
    fun `render full page`() = testApp {
        routing {
            get("/") {
                inertia.render("SampleComponent", "id" to 1, "html" to "</script>", clearHistory = true)
            }
        }

        val response = client.get("/?tab=a")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("text/html; charset=utf-8", response.headers[HttpHeaders.ContentType])
        assertEquals("X-Inertia", response.headers[HttpHeaders.Vary])
        assert("X-Inertia" !in response.headers)

        val expectedBody = """
            <!doctype html>
            <html lang="en">
            <head></head>
            <body>
                <script data-page="app" type="application/json">{"component":"SampleComponent","props":{"errors":{},"html":"{LT}\/script{GT}","id":1},"url":"\/?tab=a","version":"1","encryptHistory":true,"clearHistory":true,"sharedProps":["errors"]}</script><div id="app"></div>
            </body>
            </html>
        """.trimIndent().replace("{LT}", "\\u003C").replace("{GT}", "\\u003E")
        assertEquals(expectedBody, response.bodyAsText().trim())
    }

    @Test
    fun `render json page object with X-Inertia header`() = testApp {
        routing {
            get("/") {
                inertia.render("SampleComponent", "id" to 1, status = HttpStatusCode.NotFound)
            }
        }

        val response = client.get("/") { inertia() }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ContentType.Application.Json, response.contentType())
        assertEquals("true", response.headers["X-Inertia"])

        val expectedBody = """{"component":"SampleComponent","props":{"errors":{},"id":1},"url":"/","version":"1","encryptHistory":true,"sharedProps":["errors"]}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `render conflict with mismatching or missing X-Inertia-Version header`() = testApp {
        routing {
            get("/") {
                inertia.render("SampleComponent", "id" to 1)
            }
        }

        val staleResponse = client.get("/?page=2") {
            header("X-Inertia", "true")
            header("X-Inertia-Version", "stale-version")
        }
        val missingResponse = client.get("/") {
            header("X-Inertia", "true")
        }

        assertEquals(HttpStatusCode.Conflict, staleResponse.status)
        assertEquals("http://localhost/?page=2", staleResponse.headers["X-Inertia-Location"])
        assertEquals("1", staleResponse.headers["X-Inertia-Version"])
        assert("X-Inertia" !in staleResponse.headers)
        assert(staleResponse.bodyAsText().isEmpty())
        assertEquals(HttpStatusCode.Conflict, missingResponse.status)
    }

    @Test
    fun `redirect after put with X-Inertia header`() = testApp {
        val client = createClient { followRedirects = false }
        routing {
            put("/redirect") {
                inertia.redirect("/target")
            }
        }

        val response = client.put("/redirect") { inertia() }
        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/target", response.headers[HttpHeaders.Location])
        assert(response.bodyAsText().isEmpty())
    }

    @Test
    fun `redirect without X-Inertia header`() = testApp {
        val client = createClient { followRedirects = false }
        routing {
            get("/redirect") {
                inertia.redirect("/target#section")
            }
        }

        val response = client.get("/redirect")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/target#section", response.headers[HttpHeaders.Location])
    }

    @Test
    fun `redirect to fragment with X-Inertia header`() = testApp {
        routing {
            post("/redirect") {
                inertia.redirect("/target#section")
            }
        }

        val response = client.post("/redirect") { inertia() }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("/target#section", response.headers["X-Inertia-Redirect"])
    }

    @Test
    fun `location external redirect`() = testApp {
        val client = createClient { followRedirects = false }
        routing {
            get("/external") {
                inertia.location("https://external.example.com")
            }
        }

        val inertiaResponse = client.get("/external") { inertia() }
        val fullPageResponse = client.get("/external")

        assertEquals(HttpStatusCode.Conflict, inertiaResponse.status)
        assertEquals("https://external.example.com", inertiaResponse.headers["X-Inertia-Location"])
        assertEquals(HttpStatusCode.Found, fullPageResponse.status)
        assertEquals("https://external.example.com", fullPageResponse.headers[HttpHeaders.Location])
    }

    @Test
    fun `render with shared data merges shared props with page props`() = testApplication {
        application {
            install(Inertia) {
                versionProvider = { "1" }
                share { mapOf("appName" to "Inertia4J", "id" to 0) }
            }
        }
        routing {
            get("/") {
                inertia.share("user") { "john" }
                inertia.render("SampleComponent", "id" to 1)
            }
        }

        val response = client.get("/") { inertia() }

        val expectedBody = """{"component":"SampleComponent","props":{"appName":"Inertia4J","errors":{},"id":1,"user":"john"},"url":"/","version":"1","sharedProps":["appName","id","user","errors"]}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `render with deferred merge props lists deferred and merge metadata`() = testApp {
        routing {
            get("/") {
                inertia.render(
                    "SampleComponent",
                    "posts" to InertiaProps.defer({ listOf(1) }, "posts").merge().matchOn("id"),
                    "expensive" to { error("must not be resolved") },
                )
            }
        }

        val response = client.get("/") {
            inertia()
            header("X-Inertia-Partial-Component", "SampleComponent")
            header("X-Inertia-Partial-Data", "posts")
        }

        val expectedBody = """{"component":"SampleComponent","props":{"errors":{},"posts":[1]},"url":"/","version":"1","encryptHistory":true,"mergeProps":["posts"],"matchPropsOn":["posts.id"],"sharedProps":["errors"]}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `flash and errors are sent with the next rendered page only`() = testApp {
        val client = createClient {
            install(HttpCookies)
            followRedirects = false
        }
        routing {
            post("/records") {
                inertia.flash("message", "Created")
                inertia.errors(mapOf("name" to "Required."))
                inertia.preserveFragment()
                inertia.redirect("/records")
            }
            get("/records") {
                inertia.render("Records")
            }
        }

        client.post("/records") { inertia() }
        val firstResponse = client.get("/records") { inertia() }
        val secondResponse = client.get("/records") { inertia() }

        assertEquals(
            """{"component":"Records","props":{"errors":{"name":"Required."}},"url":"/records","version":"1","encryptHistory":true,"preserveFragment":true,"sharedProps":["errors"],"flash":{"message":"Created"}}""",
            firstResponse.bodyAsText(),
        )
        assertEquals(
            """{"component":"Records","props":{"errors":{}},"url":"/records","version":"1","encryptHistory":true,"sharedProps":["errors"]}""",
            secondResponse.bodyAsText(),
        )
    }

    @InertiaFlash
    data class SavedFlash(val message: String, val warning: String? = null)

    @Test
    fun `typed flash sends its non-null properties with the next page`() = testApp {
        val client = createClient {
            install(HttpCookies)
            followRedirects = false
        }
        routing {
            post("/records") {
                inertia.flash(SavedFlash("Created"))
                inertia.redirect("/records")
            }
            get("/records") {
                inertia.render("Records")
            }
        }

        client.post("/records") { inertia() }
        val response = client.get("/records") { inertia() }

        assertEquals(
            """{"component":"Records","props":{"errors":{}},"url":"/records","version":"1","encryptHistory":true,"sharedProps":["errors"],"flash":{"message":"Created"}}""",
            response.bodyAsText(),
        )
    }

    @Test
    fun `precognition requests are answered with validation results`() = testApp {
        routing {
            route("/users") {
                install(Precognition)
                post {
                    val errors = mapOf("name" to listOf("Required."))
                    if (inertia.isPrecognitive) {
                        inertia.precognition(errors)
                    }
                }
            }
        }

        val failingResponse = client.post("/users") { header("Precognition", "true") }
        val passingResponse = client.post("/users") {
            header("Precognition", "true")
            header("Precognition-Validate-Only", "email")
        }

        assertEquals(422, failingResponse.status.value)
        assertEquals("""{"message":"Required.","errors":{"name":["Required."]}}""", failingResponse.bodyAsText())
        assertTrue(failingResponse.headers.getAll(HttpHeaders.Vary)!!.joinToString().contains("Precognition"))
        assertEquals(HttpStatusCode.NoContent, passingResponse.status)
        assertEquals("true", passingResponse.headers["Precognition-Success"])
    }

    @Test
    fun `middleware answers outdated versions before the route runs`() = testApp {
        var handled = false
        routing {
            get("/redirect") {
                handled = true
                call.respondRedirect("/target")
            }
        }

        val response = client.get("/redirect") {
            header("X-Inertia", "true")
            header("X-Inertia-Version", "old")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("http://localhost/redirect", response.headers["X-Inertia-Location"])
        assertEquals("1", response.headers["X-Inertia-Version"])
        assertFalse(handled)
    }

    @Test
    fun `middleware turns found after put into see other`() = testApp {
        val client = createClient { followRedirects = false }
        routing {
            put("/records") {
                call.respondRedirect("/target")
            }
        }

        val inertiaResponse = client.put("/records") { inertia() }
        val plainResponse = client.put("/records")

        assertEquals(HttpStatusCode.SeeOther, inertiaResponse.status)
        assertEquals("/target", inertiaResponse.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.Found, plainResponse.status)
    }

    @Test
    fun `middleware turns redirects to a fragment into conflicts`() = testApp {
        val client = createClient { followRedirects = false }
        routing {
            post("/records") {
                call.respondRedirect("/target#comments")
            }
        }

        val response = client.post("/records") { inertia() }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("/target#comments", response.headers["X-Inertia-Redirect"])
    }

    @Test
    fun `middleware rewrites redirects sent with a body and an explicit status`() = testApp {
        val client = createClient { followRedirects = false }
        routing {
            put("/records") {
                call.response.header(HttpHeaders.Location, "/target")
                call.respondText("Redirecting", status = HttpStatusCode.Found)
            }
            post("/records") {
                call.response.header(HttpHeaders.Location, "/target#comments")
                call.respondText("Redirecting", status = HttpStatusCode.Found)
            }
        }

        val putResponse = client.put("/records") { inertia() }
        val fragmentResponse = client.post("/records") { inertia() }

        assertEquals(HttpStatusCode.SeeOther, putResponse.status)
        assertEquals("/target", putResponse.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.Conflict, fragmentResponse.status)
        assertEquals("/target#comments", fragmentResponse.headers["X-Inertia-Redirect"])
    }

    @Test
    fun `middleware redirects empty inertia responses back`() = testApp {
        val client = createClient { followRedirects = false }
        routing {
            put("/records") {
                call.respond(HttpStatusCode.OK)
            }
            get("/records") {
                call.respondText("")
            }
            get("/no-content") {
                call.respond(HttpStatusCode.NoContent)
            }
            get("/text") {
                call.respondText("plain")
            }
        }

        val putResponse = client.put("/records") {
            inertia()
            header(HttpHeaders.Referrer, "http://localhost/records/1/edit")
        }
        val getResponse = client.get("/records") { inertia() }
        val plainResponse = client.get("/records")
        val noContentResponse = client.get("/no-content") { inertia() }
        val textResponse = client.get("/text") { inertia() }

        assertEquals(HttpStatusCode.SeeOther, putResponse.status)
        assertEquals("http://localhost/records/1/edit", putResponse.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.Found, getResponse.status)
        assertEquals("/", getResponse.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.OK, plainResponse.status)
        assertEquals(HttpStatusCode.NoContent, noContentResponse.status)
        assertEquals(HttpStatusCode.OK, textResponse.status)
        assertEquals("plain", textResponse.bodyAsText())
    }

    @Test
    fun `middleware adds vary inertia to every response once`() = testApp {
        routing {
            get("/text") {
                call.respondText("plain")
            }
            get("/page") {
                inertia.render("Page")
            }
            get("/vary") {
                call.response.header(HttpHeaders.Vary, "Accept-Language")
                call.respondText("varied")
            }
        }

        val textResponse = client.get("/text")
        val pageResponse = client.get("/page")
        val inertiaPageResponse = client.get("/page") { inertia() }
        val conflictResponse = client.get("/page") {
            header("X-Inertia", "true")
            header("X-Inertia-Version", "old")
        }
        val variedResponse = client.get("/vary")

        assertEquals(listOf("X-Inertia"), textResponse.headers.getAll(HttpHeaders.Vary))
        assertEquals(listOf("X-Inertia"), pageResponse.headers.getAll(HttpHeaders.Vary))
        assertEquals(listOf("X-Inertia"), inertiaPageResponse.headers.getAll(HttpHeaders.Vary))
        assertEquals(HttpStatusCode.Conflict, conflictResponse.status)
        assertEquals(listOf("X-Inertia"), conflictResponse.headers.getAll(HttpHeaders.Vary))
        assertEquals(listOf("Accept-Language", "X-Inertia"), variedResponse.headers.getAll(HttpHeaders.Vary))
    }

    @Test
    fun `disabled middleware keeps empty responses and adds no vary header`() = testApplication {
        application {
            install(Inertia) {
                versionProvider = { "1" }
                middleware = false
            }
            routing {
                get("/records") {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        val response = client.get("/records") { inertia() }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(null, response.headers[HttpHeaders.Vary])
    }

    @Test
    fun `prefetch requests consume flash data`() = testApp {
        val client = createClient {
            install(HttpCookies)
            followRedirects = false
        }
        routing {
            post("/records") {
                inertia.flash("message", "Created")
                inertia.redirect("/records")
            }
            get("/records") {
                inertia.render("Records")
            }
        }

        client.post("/records") { inertia() }
        val prefetchResponse = client.get("/records") {
            inertia()
            header("Purpose", "prefetch")
        }
        val visitResponse = client.get("/records") { inertia() }

        assertTrue(prefetchResponse.bodyAsText().contains("Created"))
        assertFalse(visitResponse.bodyAsText().contains("Created"))
    }

    @Test
    fun `middleware can be disabled`() = testApplication {
        application {
            install(Inertia) {
                versionProvider = { "1" }
                middleware = false
            }
            routing {
                put("/records") {
                    call.respondRedirect("/target")
                }
            }
        }
        val client = createClient { followRedirects = false }

        val response = client.put("/records") { inertia() }

        assertEquals(HttpStatusCode.Found, response.status)
    }

    @Test
    fun `rendering an intentional conflict page consumes flash data`() = testApp {
        val client = createClient {
            install(HttpCookies)
            followRedirects = false
        }
        routing {
            post("/records") {
                inertia.flash("message", "Created")
                inertia.redirect("/conflict")
            }
            get("/conflict") {
                inertia.render("Conflict", status = HttpStatusCode.Conflict)
            }
            get("/records") {
                inertia.render("Records")
            }
        }

        client.post("/records") { inertia() }
        val conflictResponse = client.get("/conflict") { inertia() }
        val nextResponse = client.get("/records") { inertia() }

        assertEquals(HttpStatusCode.Conflict, conflictResponse.status)
        assertTrue(conflictResponse.bodyAsText().contains("Created"))
        assertFalse(nextResponse.bodyAsText().contains("Created"))
    }

    @Test
    fun `default flash store uses sessions when they are on the classpath`() {
        assertTrue(defaultFlashStore() is SessionsFlashStore)
    }
}
