package io.github.inertia4j.ktor

import io.github.inertia4j.annotations.InertiaPage
import io.github.inertia4j.annotations.InertiaShared
import io.github.inertia4j.core.PropertyNaming
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@InertiaPage("Records/Index")
data class RecordsIndexProps(val records: List<String>, val total: () -> Int)

@InertiaPage("Users/Show")
data class UsersShowProps(val firstName: String)

@InertiaShared
data class AppShared(val appName: String)

class InertiaKtorTypedPropsTest {
    private fun testApp(
        configure: InertiaKtorConfiguration.() -> Unit = {},
        block: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        application {
            install(Inertia) {
                versionProvider = { "1" }
                configure()
            }
        }
        block()
    }

    @Test
    fun `render typed page props uses annotated component and evaluates lazy props`() = testApp {
        routing {
            get("/") {
                inertia.render(RecordsIndexProps(listOf("a")) { 2 })
            }
        }

        val response = client.get("/") { header("X-Inertia", "true") }

        val expectedBody = """{"component":"Records/Index","props":{"records":["a"],"total":2},"url":"/","version":"1","encryptHistory":false,"clearHistory":false}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `render with component name only still renders`() = testApp {
        routing {
            get("/") {
                inertia.render("SampleComponent")
            }
        }

        val response = client.get("/") { header("X-Inertia", "true") }

        val expectedBody = """{"component":"SampleComponent","props":{},"url":"/","version":"1","encryptHistory":false,"clearHistory":false}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `shareTyped merges typed shared props`() = testApp({ shareTyped { AppShared("Inertia4J") } }) {
        routing {
            get("/") {
                inertia.render(UsersShowProps("Miles"))
            }
        }

        val response = client.get("/") { header("X-Inertia", "true") }

        val expectedBody = """{"component":"Users/Show","props":{"appName":"Inertia4J","firstName":"Miles"},"url":"/","version":"1","encryptHistory":false,"clearHistory":false}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `snake property naming renames typed props`() = testApp({
        propertyNaming = PropertyNaming.Snake
        shareTyped { AppShared("Inertia4J") }
    }) {
        routing {
            get("/") {
                inertia.render(UsersShowProps("Miles"))
            }
        }

        val response = client.get("/") { header("X-Inertia", "true") }

        val expectedBody = """{"component":"Users/Show","props":{"app_name":"Inertia4J","first_name":"Miles"},"url":"/","version":"1","encryptHistory":false,"clearHistory":false}"""
        assertEquals(expectedBody, response.bodyAsText())
    }
}
