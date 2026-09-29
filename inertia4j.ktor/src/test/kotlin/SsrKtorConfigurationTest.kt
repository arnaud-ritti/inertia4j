package io.github.inertia4j.ktor

import com.sun.net.httpserver.HttpServer
import io.github.inertia4j.core.vite.Vite
import io.github.inertia4j.core.vite.ViteConfig
import io.github.inertia4j.spi.PageObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals

class SsrKtorConfigurationTest {
    private val receivedPaths = CopyOnWriteArrayList<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            receivedPaths.add(exchange.requestURI.path)
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
}
