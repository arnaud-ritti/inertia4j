@file:Suppress("ktlint:standard:max-line-length") // expected JSON responses are kept on one line

package dev.arkoder.inertia4j.ktor

import dev.arkoder.inertia4j.annotations.InertiaPage
import dev.arkoder.inertia4j.annotations.InertiaShared
import dev.arkoder.inertia4j.core.InertiaProp
import dev.arkoder.inertia4j.core.InertiaProps
import dev.arkoder.inertia4j.core.PropertyNaming
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@InertiaPage("Records/Index")
data class RecordsIndexProps(val records: List<String>, val total: () -> Int)

@InertiaPage("Stats/Index")
data class StatsIndexProps(val stats: InertiaProp<List<Int>>)

@InertiaPage("Users/Show")
data class UsersShowProps(val firstName: String)

@InertiaShared
data class AppShared(val appName: String)

data class Address(val streetName: String)

data class Owner(val firstName: String, val homeAddress: Address)

@InertiaPage("Owners/Show")
data class OwnersShowProps(val owner: Owner)

class InertiaKtorTypedPropsTest {
    private fun testApp(configure: InertiaKtorConfiguration.() -> Unit = {}, block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                install(Sessions) {
                    cookie<InertiaSession>("INERTIA_SESSION", SessionStorageMemory()) {
                        serializer = InertiaSession.Serializer
                    }
                }
                install(Inertia) {
                    versionProvider = { "1" }
                    configure()
                }
            }
            block()
        }

    private fun HttpRequestBuilder.inertia() {
        header("X-Inertia", "true")
        header("X-Inertia-Version", "1")
    }

    @Test
    fun `render typed page props uses annotated component and evaluates lazy props`() = testApp {
        routing {
            get("/") {
                inertia.render(RecordsIndexProps(listOf("a")) { 2 })
            }
        }

        val response = client.get("/") { inertia() }

        val expectedBody = """{"component":"Records/Index","props":{"errors":{},"records":["a"],"total":2},"url":"/","version":"1","sharedProps":["errors"]}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `render with component name only still renders`() = testApp {
        routing {
            get("/") {
                inertia.render("SampleComponent")
            }
        }

        val response = client.get("/") { inertia() }

        val expectedBody = """{"component":"SampleComponent","props":{"errors":{}},"url":"/","version":"1","sharedProps":["errors"]}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `shareTyped merges typed shared props`() = testApp({ shareTyped { AppShared("Inertia4J") } }) {
        routing {
            get("/") {
                inertia.render(UsersShowProps("Miles"))
            }
        }

        val response = client.get("/") { inertia() }

        val expectedBody = """{"component":"Users/Show","props":{"appName":"Inertia4J","errors":{},"firstName":"Miles"},"url":"/","version":"1","sharedProps":["appName","errors"]}"""
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

        val response = client.get("/") { inertia() }

        val expectedBody = """{"component":"Users/Show","props":{"app_name":"Inertia4J","errors":{},"first_name":"Miles"},"url":"/","version":"1","sharedProps":["app_name","errors"]}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `snake property naming renames nested objects with the default serializer`() = testApp({
        propertyNaming = PropertyNaming.Snake
    }) {
        routing {
            get("/") {
                inertia.render(OwnersShowProps(Owner("Miles", Address("Main"))))
            }
        }

        val response = client.get("/") { inertia() }

        val expectedBody = """{"component":"Owners/Show","props":{"errors":{},"owner":{"first_name":"Miles","home_address":{"street_name":"Main"}}},"url":"/","version":"1","sharedProps":["errors"]}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `render typed page props leaves deferred InertiaProp to the resolver`() = testApp {
        routing {
            get("/") {
                inertia.render(StatsIndexProps(InertiaProps.defer { listOf(1, 2) }))
            }
        }

        val response = client.get("/") { inertia() }

        val expectedBody = """{"component":"Stats/Index","props":{"errors":{}},"url":"/","version":"1","deferredProps":{"default":["stats"]},"sharedProps":["errors"]}"""
        assertEquals(expectedBody, response.bodyAsText())
    }
}
