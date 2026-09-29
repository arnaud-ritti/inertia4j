import com.sun.net.httpserver.HttpServer;
import dev.arkoder.inertia4j.core.HttpSsrGateway;
import dev.arkoder.inertia4j.core.SsrServerProcess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SsrServerProcessTest {
    private static final String fakeServerSource = String.join("\n",
        "import com.sun.net.httpserver.HttpServer;",
        "import java.net.InetSocketAddress;",
        "public class FakeSsrServer {",
        "    public static void main(String[] args) throws Exception {",
        "        int port = Integer.parseInt(System.getenv(\"SSR_PORT\"));",
        "        HttpServer server = HttpServer.create(new InetSocketAddress(\"127.0.0.1\", port), 0);",
        "        server.createContext(\"/health\", exchange -> { exchange.sendResponseHeaders(200, -1); exchange.close(); });",
        "        server.createContext(\"/shutdown\", exchange -> {",
        "            if (System.getenv(\"IGNORE_SHUTDOWN\") == null) System.exit(0);",
        "            exchange.sendResponseHeaders(200, -1);",
        "            exchange.close();",
        "        });",
        "        server.start();",
        "    }",
        "}"
    );

    @TempDir
    Path tempDir;

    private int port;
    private HttpSsrGateway gateway;
    private SsrServerProcess process;

    @BeforeEach
    void setUp() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        gateway = HttpSsrGateway.builder().url("http://127.0.0.1:" + port).timeout(Duration.ofSeconds(1)).build();
    }

    @AfterEach
    void tearDown() {
        if (process != null) {
            process.stop();
        }
    }

    @Test
    void start_launchesBundleAndWaitsUntilHealthy() throws IOException {
        process = fakeServer(Map.of()).build();

        process.start();

        assertTrue(process.isRunning());
        assertTrue(gateway.isHealthy());
    }

    @Test
    void stop_shutsServerDownThroughShutdownEndpoint() throws IOException {
        process = fakeServer(Map.of()).shutdownTimeout(Duration.ofSeconds(10)).build();
        process.start();

        process.stop();

        assertFalse(process.isRunning());
        assertFalse(gateway.isHealthy());
    }

    @Test
    void stop_whenServerIgnoresShutdown_destroysProcess() throws IOException {
        process = fakeServer(Map.of("IGNORE_SHUTDOWN", "1")).shutdownTimeout(Duration.ofMillis(200)).build();
        process.start();

        process.stop();

        assertFalse(process.isRunning());
        assertFalse(gateway.isHealthy());
    }

    @Test
    void start_whenBundleIsMissing_throws() {
        process = SsrServerProcess.builder(gateway, tempDir.resolve("ssr.mjs")).build();

        IllegalStateException exception = assertThrows(IllegalStateException.class, process::start);

        assertTrue(exception.getMessage().startsWith("SSR bundle not found at "), exception.getMessage());
        assertFalse(process.isRunning());
    }

    @Test
    void start_whenServerExitsWhileStarting_throws() throws IOException {
        Path bundle = Files.writeString(tempDir.resolve("ssr.sh"), "exit 3\n");
        process = SsrServerProcess.builder(gateway, bundle).runtime("sh").build();

        IllegalStateException exception = assertThrows(IllegalStateException.class, process::start);

        assertEquals("SSR server exited with code 3 while starting", exception.getMessage());
        assertFalse(process.isRunning());
    }

    @Test
    void start_whenRuntimeIsMissing_throws() throws IOException {
        Path bundle = Files.writeString(tempDir.resolve("ssr.mjs"), "");
        process = SsrServerProcess.builder(gateway, bundle).runtime(tempDir.resolve("missing-node").toString()).build();

        assertThrows(IllegalStateException.class, process::start);
        assertFalse(process.isRunning());
    }

    @Test
    void start_whenServerIsNotHealthyInTime_keepsProcessRunning() throws IOException {
        Path bundle = Files.writeString(tempDir.resolve("ssr.sh"), "sleep 30\n");
        process = SsrServerProcess.builder(gateway, bundle)
            .runtime("sh")
            .startupTimeout(Duration.ofMillis(300))
            .shutdownTimeout(Duration.ofMillis(200))
            .build();

        process.start();

        assertTrue(process.isRunning());

        process.stop();

        assertFalse(process.isRunning());
    }

    @Test
    void start_whenServerAlreadyRuns_doesNotLaunchAnotherOne() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        try {
            process = SsrServerProcess.builder(gateway, tempDir.resolve("ssr.mjs")).build();

            process.start();

            assertFalse(process.isRunning());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void command_runsBundleWithRuntimeAndArguments() {
        SsrServerProcess serverProcess = SsrServerProcess.builder(gateway, Path.of("bootstrap/ssr/ssr.mjs"))
            .runtime("bun")
            .arguments(List.of("--smol"))
            .build();

        assertEquals(List.of("bun", "--smol", Path.of("bootstrap/ssr/ssr.mjs").toString()), serverProcess.command());
    }

    @Test
    void command_defaultsToNode() {
        SsrServerProcess serverProcess = SsrServerProcess.builder(gateway, Path.of("ssr.mjs")).build();

        assertEquals(List.of("node", "ssr.mjs"), serverProcess.command());
    }

    private SsrServerProcess.Builder fakeServer(Map<String, String> environment) throws IOException {
        Path bundle = Files.writeString(tempDir.resolve("FakeSsrServer.java"), fakeServerSource);
        Map<String, String> processEnvironment = new HashMap<>(environment);
        processEnvironment.put("SSR_PORT", String.valueOf(port));

        return SsrServerProcess.builder(gateway, bundle)
            .runtime(javaExecutable())
            .workingDirectory(tempDir)
            .environment(processEnvironment)
            .startupTimeout(Duration.ofSeconds(30));
    }

    private static String javaExecutable() {
        return ProcessHandle.current().info().command()
            .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }
}
