package dev.arkoder.inertia4j.core.vite;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteTagAttributesTest {
    @TempDir
    Path tempDir;

    private Vite vite(String buildDirectory, Supplier<@Nullable String> nonceProvider) {
        return vite(ViteConfig.builder().buildDirectory(buildDirectory).nonceProvider(nonceProvider));
    }

    private Vite vite(ViteConfig.Builder builder) {
        return new Vite(builder.hotFile(tempDir.resolve("vite.hot")).build());
    }

    private void startDevServer() throws Exception {
        Files.writeString(tempDir.resolve("vite.hot"), "http://localhost:5173");
    }

    @Test
    void tags_withIntegrity_addsIntegrityAndCrossOriginToEveryTag() {
        String expected = String.join("\n",
            "<link rel=\"stylesheet\" href=\"/build/assets/main-5UjPuW-k.css\" integrity=\"sha384-css\" crossorigin=\"anonymous\">",
            "<script type=\"module\" src=\"/build/assets/main-BRBmoGS9.js\" integrity=\"sha384-main&quot;&lt;\" crossorigin=\"anonymous\"></script>",
            "<link rel=\"modulepreload\" href=\"/build/assets/shared-B7PI925R.js\" integrity=\"sha384-shared\" crossorigin=\"anonymous\">"
        );

        assertEquals(expected, vite("vite-sri", () -> null).tags("src/main.ts"));
    }

    @Test
    void tags_withCustomIntegrityKey_readsThatField() {
        String tags = vite(ViteConfig.builder().buildDirectory("vite-sri").integrityKey("sri")).tags("src/main.ts");

        assertTrue(tags.contains("<link rel=\"stylesheet\" href=\"/build/assets/main-5UjPuW-k.css\">"));
        assertTrue(tags.contains("src=\"/build/assets/main-BRBmoGS9.js\" integrity=\"sha512-main\" crossorigin=\"anonymous\">"));
        assertTrue(tags.contains("href=\"/build/assets/shared-B7PI925R.js\" integrity=\"sha512-shared\" crossorigin=\"anonymous\">"));
    }

    @Test
    void tags_whenIntegrityIsDisabled_omitsIntegrity() {
        String tags = vite(ViteConfig.builder().buildDirectory("vite-sri").integrityKey(null)).tags("src/main.ts");

        assertFalse(tags.contains("integrity"));
        assertFalse(tags.contains("crossorigin"));
    }

    @Test
    void tags_withNonce_addsEscapedNonceToEveryTag() {
        String expected = String.join("\n",
            "<link rel=\"stylesheet\" href=\"/build/assets/foo-5UjPuW-k.css\" nonce=\"a&quot;b\">",
            "<link rel=\"stylesheet\" href=\"/build/assets/shared-ChJ_j-JJ.css\" nonce=\"a&quot;b\">",
            "<link rel=\"stylesheet\" href=\"/build/assets/util-Cq0cE3aP.css\" nonce=\"a&quot;b\">",
            "<script type=\"module\" src=\"/build/assets/foo-BRBmoGS9.js\" nonce=\"a&quot;b\"></script>",
            "<link rel=\"modulepreload\" href=\"/build/assets/shared-B7PI925R.js\" nonce=\"a&quot;b\">",
            "<link rel=\"modulepreload\" href=\"/build/assets/util-D1gK2mQx.js\" nonce=\"a&quot;b\">"
        );

        assertEquals(expected, vite("vite-fixture", () -> "a\"b").tags("views/foo.js"));
    }

    @Test
    void tags_withNonceAndIntegrity_rendersNonceBeforeIntegrity() {
        String tags = vite("vite-sri", () -> "r4nd0m").tags("src/main.ts");

        assertTrue(tags.contains(
            "<link rel=\"modulepreload\" href=\"/build/assets/shared-B7PI925R.js\" nonce=\"r4nd0m\" integrity=\"sha384-shared\" crossorigin=\"anonymous\">"
        ));
    }

    @Test
    void tags_whenNonceIsEmpty_omitsNonce() {
        assertFalse(vite("vite-fixture", () -> "").tags("views/foo.js").contains("nonce"));
    }

    @Test
    void tags_readsNonceOnEveryCall() {
        AtomicInteger counter = new AtomicInteger();
        Vite vite = vite("vite-fixture", () -> "n" + counter.incrementAndGet());

        assertTrue(vite.tags("styles/app.css").contains("nonce=\"n1\""));
        assertTrue(vite.tags("styles/app.css").contains("nonce=\"n2\""));
    }

    @Test
    void tags_inDevMode_addsNonceWithoutIntegrity() throws Exception {
        startDevServer();

        String expected = String.join("\n",
            "<script type=\"module\" src=\"http://localhost:5173/@vite/client\" nonce=\"abc\"></script>",
            "<script type=\"module\" src=\"http://localhost:5173/src/main.ts\" nonce=\"abc\"></script>",
            "<link rel=\"stylesheet\" href=\"http://localhost:5173/main.css\" nonce=\"abc\">"
        );

        assertEquals(expected, vite("vite-sri", () -> "abc").tags("src/main.ts", "main.css"));
    }

    @Test
    void reactRefreshTag_withNonce_addsNonceToPreamble() throws Exception {
        startDevServer();

        assertTrue(vite("vite-fixture", () -> "abc").reactRefreshTag().startsWith("<script type=\"module\" nonce=\"abc\">\n"));
    }

    @Test
    void cspNonce_whenProviderReturnsEmpty_isNull() {
        assertNull(vite("vite-fixture", () -> "").cspNonce());
        assertEquals("abc", vite("vite-fixture", () -> "abc").cspNonce());
    }
}
