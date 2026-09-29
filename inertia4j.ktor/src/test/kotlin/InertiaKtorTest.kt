package io.github.inertia4j.ktor

import io.github.inertia4j.core.InertiaProps
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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
                    "expensive" to { error("must not be resolved") }
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
            firstResponse.bodyAsText()
        )
        assertEquals(
            """{"component":"Records","props":{"errors":{}},"url":"/records","version":"1","encryptHistory":true,"sharedProps":["errors"]}""",
            secondResponse.bodyAsText()
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
}
