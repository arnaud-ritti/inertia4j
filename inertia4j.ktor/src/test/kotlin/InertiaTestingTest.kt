package dev.arkoder.inertia4j.ktor

import dev.arkoder.inertia4j.core.InertiaProps
import dev.arkoder.inertia4j.ktor.testing.assertInertia
import dev.arkoder.inertia4j.ktor.testing.inertiaPage
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InertiaTestingTest {
    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(Sessions) {
                cookie<InertiaSession>("INERTIA_SESSION", SessionStorageMemory()) {
                    serializer = InertiaSession.Serializer
                }
            }
            install(Inertia) {
                versionProvider = { "1" }
            }
        }
        routing {
            get("/users") {
                inertia.render(
                    "Users/Index",
                    "users" to listOf(mapOf("id" to 1, "name" to "Jane"), mapOf("id" to 2, "name" to "John")),
                    "search" to (call.request.queryParameters["search"] ?: ""),
                    "permissions" to InertiaProps.defer { listOf("users.edit") },
                    "stats" to InertiaProps.defer({ mapOf("visits" to 42) }, "stats"),
                )
            }
            post("/users") {
                inertia.flash("message", "User created")
                inertia.errors(mapOf("name" to "The name is required."))
                inertia.redirect("/users")
            }
        }
        block()
    }

    @Test
    fun `asserts full page visits`() = testApp {
        client.get("/users").assertInertia {
            component("Users/Index")
            url("/users")
            version("1")
            has("users", 2) { user -> user.where("id", 1).where("name", "Jane") }
            where("search", "")
            missing("permissions")
            hasDeferredProp("permissions")
            hasDeferredProp("stats", "stats")
            hasNoErrors()
        }
    }

    @Test
    fun `asserts inertia requests`() = testApp {
        val page = client.get("/users?search=ja") {
            header("X-Inertia", "true")
            header("X-Inertia-Version", "1")
        }.inertiaPage()

        assertEquals("ja", page.prop("search"))
        assertEquals("/users?search=ja", page.url)
    }

    @Test
    fun `fails on unexpected pages`() = testApp {
        val error = assertFailsWith<AssertionError> {
            client.get("/users").assertInertia { component("Users/Show") }
        }

        assertEquals("Unexpected Inertia page component. Expected: \"Users/Show\", actual: \"Users/Index\"", error.message)
    }

    @Test
    fun `fails on redirects`() = testApp {
        val noRedirectClient = createClient { followRedirects = false }

        val error = assertFailsWith<AssertionError> {
            noRedirectClient.post("/users").assertInertia()
        }

        assertEquals("Not a valid Inertia response: redirect 302 to /users", error.message)
    }

    @Test
    fun `loads deferred props and reloads partially`() = testApp {
        client.get("/users?search=ja").assertInertia {
            loadDeferredProps { deferred ->
                deferred.where("permissions", listOf("users.edit")).where("stats.visits", 42).missing("users")
            }
            loadDeferredProps("stats") { deferred -> deferred.has("stats").missing("permissions") }
            reloadOnly("search") { reloaded -> reloaded.where("search", "ja").missing("users") }
            reloadExcept("users") { reloaded -> reloaded.has("search") }
            reload { reloaded -> reloaded.has("users", 2).missing("permissions") }
        }
    }

    @Test
    fun `asserts flash and errors after redirect`() = testApp {
        val cookieClient = createClient {
            install(HttpCookies)
        }

        cookieClient.post("/users") { header("X-Inertia", "true") }
        cookieClient.get("/users") {
            header("X-Inertia", "true")
            header("X-Inertia-Version", "1")
        }.assertInertia {
            hasFlash("message", "User created")
            missingFlash("error")
            hasError("name", "The name is required.")
        }

        assertTrue(cookieClient.get("/users").inertiaPage().flash.isEmpty())
    }

    @Test
    fun `carries session cookies over to reload requests without a cookie plugin`() = testApp {
        val noRedirectClient = createClient { followRedirects = false }
        val redirect = noRedirectClient.post("/users") { header("X-Inertia", "true") }
        val sessionCookie = redirect.headers["Set-Cookie"]!!.substringBefore(';')

        noRedirectClient.get("/users") {
            header("X-Inertia", "true")
            header("X-Inertia-Version", "1")
            header("Cookie", sessionCookie)
        }.assertInertia {
            hasFlash("message")
            reloadOnly("users") { reloaded -> reloaded.missingFlash("message") }
        }
    }
}
