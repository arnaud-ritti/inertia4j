package dev.arkoder.inertia4j.ktor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class ViteKtorTest {
    private fun viteApp(
        configure: InertiaKtorConfiguration.() -> Unit = {},
        block: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        application {
            install(Inertia) {
                templatePath = "templates/vite.html"
                vite {
                    buildDirectory = "vite-fixture"
                    cacheMaxAge = 1.hours
                }
                configure()
            }
            routing {
                get("/") {
                    inertia.render("Home")
                }
            }
        }
        block()
    }

    private fun manifestHash(): String {
        val bytes = javaClass.classLoader.getResourceAsStream("vite-fixture/.vite/manifest.json")!!.readBytes()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `full page visit renders Vite tags from manifest`() = viteApp {
        val body = client.get("/").bodyAsText()

        assertContains(
            body,
            "<link rel=\"stylesheet\" href=\"/build/assets/main-5UjPuW-k.css\">\n" +
                "<script type=\"module\" src=\"/build/assets/main-BRBmoGS9.js\"></script>"
        )
    }

    @Test
    fun `stale asset version returns conflict`() = viteApp {
        val response = client.get("/") {
            header("X-Inertia", "true")
            header("X-Inertia-Version", "stale")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `current asset version returns page`() = viteApp {
        val version = manifestHash()

        val response = client.get("/") {
            header("X-Inertia", "true")
            header("X-Inertia-Version", version)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"version\":\"$version\"")
    }

    @Test
    fun `built asset is served with immutable cache headers`() = viteApp {
        val response = client.get("/build/assets/main-BRBmoGS9.js")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("public, max-age=3600, immutable", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `explicit version provider overrides Vite version`() = viteApp(configure = { versionProvider = { "custom" } }) {
        val response = client.get("/") {
            header("X-Inertia", "true")
            header("X-Inertia-Version", "custom")
        }

        assertContains(response.bodyAsText(), "\"version\":\"custom\"")
    }

    @Test
    fun `assets are not served when disabled`() = viteApp(configure = { vite { serveAssets = false } }) {
        val response = client.get("/build/assets/main-BRBmoGS9.js")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
