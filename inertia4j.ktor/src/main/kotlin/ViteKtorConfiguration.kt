package dev.arkoder.inertia4j.ktor

import dev.arkoder.inertia4j.core.vite.ViteConfig
import io.ktor.server.application.*
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Vite integration settings of the Inertia plugin.
 * Defaults match the `vite.config.ts` documented in `docs/guides/vite.md`.
 */
class ViteKtorConfiguration {
    /**
     * File whose presence, written by the Vite dev server, enables dev mode. Defaults to `vite.hot`.
     */
    var hotFile: Path = Path.of("vite.hot")

    /**
     * Classpath directory containing the Vite build output. Defaults to `static/build`.
     */
    var buildDirectory: String = "static/build"

    /**
     * Classpath location of the Vite manifest. Defaults to `null`, meaning `$buildDirectory/.vite/manifest.json`.
     */
    var manifestPath: String? = null

    /**
     * URL prefix under which built files are served. Defaults to `/build/`.
     */
    var publicPath: String = "/build/"

    /**
     * Whether the plugin serves [buildDirectory] under [publicPath]. Defaults to `true`.
     */
    var serveAssets: Boolean = true

    /**
     * `max-age` of the `Cache-Control` header sent with built files. Defaults to 365 days.
     */
    var cacheMaxAge: Duration = 365.days

    /**
     * Manifest field holding the Subresource Integrity hash of chunks, written by `vite-plugin-manifest-sri`.
     * Defaults to `integrity`; `null` renders tags without `integrity` attribute.
     */
    var integrityKey: String? = ViteConfig.DefaultIntegrityKey

    /**
     * Resolves the Content Security Policy nonce of a call, added to the Vite tags of its page. Defaults to `null`,
     * rendering tags without nonce.
     */
    var nonce: ((ApplicationCall) -> String?)? = null

    internal val currentNonce = ThreadLocal<String?>()

    internal fun toViteConfig(): ViteConfig = ViteConfig.builder()
        .hotFile(hotFile)
        .buildDirectory(buildDirectory)
        .manifestPath(manifestPath)
        .publicPath(publicPath)
        .integrityKey(integrityKey)
        .nonceProvider(Supplier { currentNonce.get() })
        .build()
}
