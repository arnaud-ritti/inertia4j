package dev.arkoder.inertia4j.ktor

import com.sun.net.httpserver.HttpServer
import dev.arkoder.inertia4j.core.HttpSsrGateway
import dev.arkoder.inertia4j.core.vite.Vite
import dev.arkoder.inertia4j.core.vite.ViteConfig
import dev.arkoder.inertia4j.spi.PageObject
import io.ktor.server.application.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SsrKtorConfigurationTest {
    private val receivedPaths = CopyOnWriteArrayList<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            receivedPaths.add(exchange.requestURI.path)
            if (exchange.requestURI.path == "/health") {
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return@createContext
            }
            val body = """{"head":[],"body":"<div></div>"}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }
    private val serverUrl = "http://127.0.0.1:${server.address.port}"

    @TempDir
    lateinit var tempDir: Path

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `fixed hot url is only used while the Vite dev server runs`() {
        val hotFile = tempDir.resolve("vite.hot")
        val vite = Vite(ViteConfig.builder().hotFile(hotFile).build())
        val gateway = InertiaKtorConfiguration.SsrConfiguration().apply {
            enabled = true
            url = serverUrl
            hotUrl = serverUrl
        }.gatewayOrDefault(vite) {}!!
        val page = PageObject.builder("Home", "/", "1").build()

        gateway.render(page, "{}")
        Files.writeString(hotFile, "http://localhost:5173")
        gateway.render(page, "{}")

        assertEquals(listOf("/render", "/__inertia_ssr"), receivedPaths)
    }

    @Test
    fun `missing bundle renders client-side without contacting the server`() {
        val gateway = ssrConfiguration {
            bundle = tempDir.resolve("ssr.mjs")
        }.gatewayOrDefault(vite()) { throw AssertionError("No failure expected") }!!

        assertNull(gateway.render(page, "{}"))
        assertEquals(emptyList(), receivedPaths)
    }

    @Test
    fun `existing bundle renders with the server`() {
        val bundle = Files.writeString(tempDir.resolve("ssr.mjs"), "")
        val gateway = ssrConfiguration { this.bundle = bundle }.gatewayOrDefault(vite()) {}!!

        assertNotNull(gateway.render(page, "{}"))
        assertEquals(listOf("/render"), receivedPaths)
    }

    @Test
    fun `disabled bundle check renders with the server`() {
        val gateway = ssrConfiguration {
            bundle = tempDir.resolve("ssr.mjs")
            ensureBundleExists = false
        }.gatewayOrDefault(vite()) {}!!

        gateway.render(page, "{}")

        assertEquals(listOf("/render"), receivedPaths)
    }

    @Test
    fun `server gateway reuses the default gateway`() {
        val configuration = ssrConfiguration {}
        val gateway = configuration.gatewayOrDefault(vite()) {}!!

        assertSame(gateway, configuration.serverGateway(gateway, vite()))
    }

    @Test
    fun `server gateway reaches the configured url with a custom gateway`() {
        val configuration = ssrConfiguration { gateway = dev.arkoder.inertia4j.spi.SsrGateway { _, _ -> null } }
        val serverGateway = configuration.serverGateway(configuration.gatewayOrDefault(vite()) {}!!, vite())

        assertEquals(serverUrl, serverGateway.url)
        assertTrue(serverGateway.isHealthy())
    }

    @Test
    fun `process runs the bundle with the runtime`() {
        val configuration = ssrConfiguration {
            bundle = Path.of("build/ssr/ssr.mjs")
            process {
                enabled = true
                runtime = "bun"
                arguments = listOf("--smol")
            }
        }
        val process = configuration.serverProcessOrNull(HttpSsrGateway.builder().build())!!

        assertEquals(listOf("bun", "--smol", Path.of("build/ssr/ssr.mjs").toString()), process.command())
    }

    @Test
    fun `process is disabled by default`() {
        assertNull(ssrConfiguration {}.serverProcessOrNull(HttpSsrGateway.builder().build()))
    }

    @Test
    fun `process requires a bundle`() {
        val configuration = ssrConfiguration { process { enabled = true } }

        val exception = assertFailsWith<IllegalStateException> {
            configuration.serverProcessOrNull(HttpSsrGateway.builder().build())
        }

        assertEquals("ssr.process.enabled requires ssr.bundle to be set", exception.message)
    }

    @Test
    fun `startup check warns when the server is unreachable`() {
        server.stop(0)
        val warnings = mutableListOf<String>()

        SsrServerOperator(httpGateway(), null, true) { message, _ -> warnings.add(message) }.start()

        assertEquals(listOf("Inertia SSR server is not reachable at $serverUrl, pages are rendered client-side"), warnings)
    }

    @Test
    fun `startup check is quiet when the server is healthy`() {
        val warnings = mutableListOf<String>()

        SsrServerOperator(httpGateway(), null, true) { message, _ -> warnings.add(message) }.start()

        assertEquals(emptyList(), warnings)
    }

    @Test
    fun `startup check is skipped while the Vite dev server renders pages`() {
        server.stop(0)
        val warnings = mutableListOf<String>()
        val gateway = HttpSsrGateway.builder().url(serverUrl).hotUrl("http://localhost:5173").build()

        SsrServerOperator(gateway, null, true) { message, _ -> warnings.add(message) }.start()

        assertEquals(emptyList(), warnings)
    }

    @Test
    fun `process failing to start is reported as a warning`() {
        server.stop(0)
        val warnings = mutableListOf<String>()
        val gateway = httpGateway()
        val process = ssrConfiguration {
            bundle = tempDir.resolve("ssr.mjs")
            process { enabled = true }
        }.serverProcessOrNull(gateway)

        SsrServerOperator(gateway, process, false) { message, _ -> warnings.add(message) }.start()

        assertEquals(1, warnings.size)
        assertContains(warnings[0], "Unable to start the Inertia SSR server: SSR bundle not found at")
    }

    @Test
    fun `plugin runs the process while the application runs`() {
        server.stop(0)
        val started = tempDir.resolve("started")
        val stopped = tempDir.resolve("stopped")
        val bundle = Files.writeString(
            tempDir.resolve("ssr.sh"),
            "trap 'echo > \"$stopped\"; exit 0' TERM\necho > \"$started\"\nsleep 30 &\nwait\n",
        )

        testApplication {
            application {
                install(Inertia) {
                    vite { hotFile = tempDir.resolve("vite.hot") }
                    ssr {
                        enabled = true
                        url = serverUrl
                        this.bundle = bundle
                        process {
                            enabled = true
                            runtime = "sh"
                            startupTimeout = Duration.ofMillis(500)
                            shutdownTimeout = Duration.ofSeconds(5)
                        }
                    }
                }
            }

            startApplication()

            assertTrue(Files.exists(started))
            assertFalse(Files.exists(stopped))
        }

        assertTrue(Files.exists(stopped))
    }

    private val page = PageObject.builder("Home", "/", "1").build()

    private fun vite() = Vite(ViteConfig.builder().hotFile(tempDir.resolve("vite.hot")).build())

    private fun httpGateway() = HttpSsrGateway.builder().url(serverUrl).build()

    private fun ssrConfiguration(configure: InertiaKtorConfiguration.SsrConfiguration.() -> Unit) =
        InertiaKtorConfiguration.SsrConfiguration().apply {
            enabled = true
            url = serverUrl
            configure()
        }
}
