package dev.arkoder.inertia4j.ktor

import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.assertEquals

class InertiaRoutesTest {
    private fun Application.routes() {
        routing {
            get("/") { call.respondText("home") }
            route("/users") {
                get { call.respondText("index") }.named("users.index")
                get("/{id}") { call.respondText("show") }.named("users.show")
                put("/{id}") { call.respondText("update") }
            }
            get("/reports/{year}/{month?}") { call.respondText("report") }
            get("/files/{path...}") { call.respondText("file") }
            get("/legacy/*") { call.respondText("legacy") }
        }
    }

    @Test
    fun `the manifest lists routes with names, methods and templates`() = testApplication {
        lateinit var manifest: String
        application {
            install(Inertia)
            routes()
            manifest = inertiaRouteManifest()
        }
        startApplication()

        assertEquals(
            """
            {
              "version": 1,
              "routes": [
                { "name": null, "methods": ["get"], "path": "/" },
                { "name": "users.index", "methods": ["get"], "path": "/users" },
                { "name": "users.show", "methods": ["get"], "path": "/users/{id}" },
                { "name": null, "methods": ["put"], "path": "/users/{id}" },
                { "name": null, "methods": ["get"], "path": "/reports/{year}/{month?}" },
                { "name": null, "methods": ["get"], "path": "/files/{*path}" }
              ]
            }

            """.trimIndent(),
            manifest,
        )
    }

    @Test
    fun `the plugin writes the manifest when the application starts`() = testApplication {
        val file = createTempDirectory().resolve("inertia/routes.json")
        application {
            install(Inertia) { routeManifest = file }
            routes()
        }

        client.get("/")

        assertEquals(true, Files.exists(file))
        assertEquals(true, file.readText().contains("\"users.show\""))
    }
}
