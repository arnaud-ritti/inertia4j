package io.github.inertia4j.ktor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ViteTemplateKtorTest {
    private val cspNonceKey = AttributeKey<String>("cspNonce")

    private fun viteApp(
        configure: ViteKtorConfiguration.() -> Unit = {},
        block: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        application {
            install(Inertia) {
                templatePath = "templates/vite-template.html"
                vite {
                    buildDirectory = "vite-sri"
                    configure()
                }
            }
            routing {
                get("/") {
                    call.request.queryParameters["nonce"]?.let { call.attributes.put(cspNonceKey, it) }
                    inertia.render("Home")
                }
                get("/logo") {
                    call.respondText(inertia.vite.asset("src/images/logo.png"))
                }
            }
        }
        block()
    }

    @Test
    fun `full page visit renders integrity of manifest chunks`() = viteApp {
        val body = client.get("/").bodyAsText()

        assertContains(
            body,
            "<link rel=\"stylesheet\" href=\"/build/assets/main-5UjPuW-k.css\" integrity=\"sha384-css\" crossorigin=\"anonymous\">\n" +
                "<script type=\"module\" src=\"/build/assets/main-BRBmoGS9.js\" integrity=\"sha384-main\" crossorigin=\"anonymous\"></script>"
        )
        assertFalse(body.contains("nonce"))
    }

    @Test
    fun `disabled integrity key renders tags without integrity`() = viteApp(configure = { integrityKey = null }) {
        assertFalse(client.get("/").bodyAsText().contains("integrity"))
    }

    @Test
    fun `nonce resolver adds the nonce of each call`() = viteApp(configure = { nonce = { it.attributes.getOrNull(cspNonceKey) } }) {
        val first = client.get("/?nonce=first").bodyAsText()
        val second = client.get("/?nonce=second").bodyAsText()
        val none = client.get("/").bodyAsText()

        assertContains(first, "src=\"/build/assets/main-BRBmoGS9.js\" nonce=\"first\" integrity=\"sha384-main\"")
        assertContains(second, "src=\"/build/assets/main-BRBmoGS9.js\" nonce=\"second\" integrity=\"sha384-main\"")
        assertFalse(none.contains("nonce"))
    }

    @Test
    fun `template without SSR renders head fallback`() = viteApp {
        assertContains(client.get("/").bodyAsText(), "<title>Fallback</title>\n  </head>")
    }

    @Test
    fun `template renders asset placeholder`() = viteApp {
        assertContains(client.get("/").bodyAsText(), "<img src=\"/build/assets/logo-Dx8Kp2Qa.png\" alt=\"\">")
    }

    @Test
    fun `renderer exposes Vite to resolve asset urls`() = viteApp {
        assertEquals("/build/assets/logo-Dx8Kp2Qa.png", client.get("/logo").bodyAsText())
    }
}
