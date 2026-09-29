package io.github.inertia4j.springboot3;

import com.sun.net.httpserver.HttpServer;
import io.github.inertia4j.core.HttpSsrGateway;
import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.SsrGateway;
import io.github.inertia4j.springshared.InertiaConfigurationProperties;
import io.github.inertia4j.springshared.InertiaSsrServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class InertiaSsrServerTest {
    private static final PageObject pageObject = PageObject.builder("Home", "/", "1").build();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(InertiaSpringAutoconfiguration.class));
    private final AtomicInteger renderRequests = new AtomicInteger();

    @TempDir
    Path tempDir;

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/render", exchange -> {
            renderRequests.incrementAndGet();
            byte[] body = "{\"head\":[],\"body\":\"<div></div>\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void ssrProperties_areBound() {
        contextRunner.withPropertyValues(
            "inertia.ssr.bundle=build/ssr/ssr.mjs",
            "inertia.ssr.ensure-bundle-exists=false",
            "inertia.ssr.check-on-startup=true",
            "inertia.ssr.process.runtime=bun",
            "inertia.ssr.process.arguments=--smol,--silent",
            "inertia.ssr.process.working-directory=frontend",
            "inertia.ssr.process.environment.NODE_ENV=production",
            "inertia.ssr.process.startup-timeout=3s",
            "inertia.ssr.process.shutdown-timeout=1s"
        ).run(context -> {
            InertiaConfigurationProperties.Ssr ssr = context.getBean(InertiaConfigurationProperties.class).getSsr();

            assertThat(ssr.getBundle()).isEqualTo("build/ssr/ssr.mjs");
            assertThat(ssr.isEnsureBundleExists()).isFalse();
            assertThat(ssr.isCheckOnStartup()).isTrue();
            assertThat(ssr.getProcess().isEnabled()).isFalse();
            assertThat(ssr.getProcess().getRuntime()).isEqualTo("bun");
            assertThat(ssr.getProcess().getArguments()).containsExactly("--smol", "--silent");
            assertThat(ssr.getProcess().getWorkingDirectory()).isEqualTo("frontend");
            assertThat(ssr.getProcess().getEnvironment()).isEqualTo(Map.of("NODE_ENV", "production"));
            assertThat(ssr.getProcess().getStartupTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(ssr.getProcess().getShutdownTimeout()).isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    void ssrProperties_defaultWhenUnset() {
        contextRunner.run(context -> {
            InertiaConfigurationProperties.Ssr ssr = context.getBean(InertiaConfigurationProperties.class).getSsr();

            assertThat(ssr.getBundle()).isNull();
            assertThat(ssr.isEnsureBundleExists()).isTrue();
            assertThat(ssr.isCheckOnStartup()).isFalse();
            assertThat(ssr.getProcess().isEnabled()).isFalse();
            assertThat(ssr.getProcess().getRuntime()).isEqualTo("node");
            assertThat(ssr.getProcess().getArguments()).isEmpty();
            assertThat(ssr.getProcess().getStartupTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(ssr.getProcess().getShutdownTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(context).doesNotHaveBean(InertiaSsrServer.class);
            assertThat(context).doesNotHaveBean("inertiaSsrHealthIndicator");
        });
    }

    @Test
    void ssrGateway_whenBundleIsMissing_rendersClientSideWithoutContactingServer() {
        ssrContext("inertia.ssr.bundle=" + tempDir.resolve("ssr.mjs")).run(context -> {
            assertThat(context.getBean(SsrGateway.class).render(pageObject, "{}")).isNull();
            assertThat(renderRequests).hasValue(0);
        });
    }

    @Test
    void ssrGateway_whenBundleExists_rendersWithServer() throws IOException {
        Path bundle = Files.writeString(tempDir.resolve("ssr.mjs"), "");

        ssrContext("inertia.ssr.bundle=" + bundle).run(context -> {
            assertThat(context.getBean(SsrGateway.class).render(pageObject, "{}")).isNotNull();
            assertThat(renderRequests).hasValue(1);
        });
    }

    @Test
    void healthIndicator_reportsServerHealth() {
        ssrContext().run(context -> {
            InertiaSsrHealthIndicator indicator = context.getBean("inertiaSsrHealthIndicator", InertiaSsrHealthIndicator.class);

            Health up = indicator.health();
            assertThat(up.getStatus()).isEqualTo(Status.UP);
            assertThat(up.getDetails()).containsEntry("url", serverUrl());

            server.stop(0);
            assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        });
    }

    @Test
    void healthIndicator_isContributedAsInertiaSsr() {
        ssrContext()
            .withConfiguration(AutoConfigurations.of(HealthContributorAutoConfiguration.class, HealthEndpointAutoConfiguration.class))
            .run(context ->
                assertThat(context.getBean(HealthContributorRegistry.class).getContributor("inertiaSsr"))
                    .isInstanceOf(InertiaSsrHealthIndicator.class)
            );
    }

    @Test
    void healthIndicator_whileViteDevServerRenders_reportsUp() throws IOException {
        Path hotFile = Files.writeString(tempDir.resolve("vite.hot"), "http://localhost:5173");
        server.stop(0);

        ssrContext("inertia.vite.hot-file=" + hotFile).run(context -> {
            Health health = context.getBean(InertiaSsrHealthIndicator.class).health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("url", "http://localhost:5173");
            assertThat(health.getDetails()).containsEntry("viteDevServer", true);
        });
    }

    @Test
    void healthIndicator_whenDisabled_isNotRegistered() {
        ssrContext("management.health.inertia-ssr.enabled=false").run(context ->
            assertThat(context).doesNotHaveBean("inertiaSsrHealthIndicator")
        );
    }

    @Test
    void healthIndicator_whenActuatorIsMissing_isNotRegistered() {
        ssrContext()
            .withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate"))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(InertiaSsrServer.class);
                assertThat(context).doesNotHaveBean("inertiaSsrHealthIndicator");
            });
    }

    @Test
    void checkOnStartup_whenServerIsUnreachable_logsWarning(CapturedOutput output) {
        server.stop(0);

        ssrContext("inertia.ssr.check-on-startup=true").run(context ->
            assertThat(output).contains("Inertia SSR server is not reachable at " + serverUrl())
        );
    }

    @Test
    void checkOnStartup_whenServerIsHealthy_logsNothing(CapturedOutput output) {
        ssrContext("inertia.ssr.check-on-startup=true").run(context ->
            assertThat(output).doesNotContain("Inertia SSR server is not reachable")
        );
    }

    @Test
    void checkOnStartup_whileViteDevServerRenders_isSkipped(CapturedOutput output) throws IOException {
        Path hotFile = Files.writeString(tempDir.resolve("vite.hot"), "http://localhost:5173");
        server.stop(0);

        ssrContext("inertia.ssr.check-on-startup=true", "inertia.vite.hot-file=" + hotFile).run(context ->
            assertThat(output).doesNotContain("Inertia SSR server is not reachable")
        );
    }

    @Test
    void process_runsBundleWhileContextRuns() throws IOException {
        server.stop(0);
        Path bundle = Files.writeString(tempDir.resolve("ssr.sh"), "sleep 30\n");
        InertiaSsrServer[] ssrServer = new InertiaSsrServer[1];

        processContext(bundle).run(context -> {
            ssrServer[0] = context.getBean(InertiaSsrServer.class);

            assertThat(ssrServer[0].getProcess().command()).isEqualTo(List.of("sh", bundle.toString()));
            assertThat(ssrServer[0].getProcess().isRunning()).isTrue();
        });

        assertThat(ssrServer[0].getProcess().isRunning()).isFalse();
    }

    @Test
    void process_whileViteDevServerRenders_isNotStarted() throws IOException {
        server.stop(0);
        Path bundle = Files.writeString(tempDir.resolve("ssr.sh"), "sleep 30\n");
        Path hotFile = Files.writeString(tempDir.resolve("vite.hot"), "http://localhost:5173");

        processContext(bundle).withPropertyValues("inertia.vite.hot-file=" + hotFile).run(context ->
            assertThat(context.getBean(InertiaSsrServer.class).getProcess().isRunning()).isFalse()
        );
    }

    @Test
    void process_whenBundleIsMissing_logsWarningAndStarts(CapturedOutput output) {
        server.stop(0);

        processContext(tempDir.resolve("ssr.mjs")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(output).contains("Unable to start the Inertia SSR server: SSR bundle not found at");
        });
    }

    @Test
    void process_withoutBundle_failsContext() {
        ssrContext("inertia.ssr.process.enabled=true").run(context ->
            assertThat(context).getFailure()
                .hasRootCauseMessage("inertia.ssr.process.enabled requires inertia.ssr.bundle to be set")
        );
    }

    @Test
    void inertiaSsrServer_reusesHttpSsrGatewayBean() {
        ssrContext().run(context ->
            assertThat(context.getBean(InertiaSsrServer.class).getGateway()).isSameAs(context.getBean(SsrGateway.class))
        );
    }

    @Test
    void inertiaSsrServer_withCustomGateway_reachesConfiguredUrl() {
        ssrContext()
            .withBean(SsrGateway.class, () -> (page, json) -> null)
            .run(context -> {
                HttpSsrGateway gateway = context.getBean(InertiaSsrServer.class).getGateway();

                assertThat(gateway.getUrl()).isEqualTo(serverUrl());
                assertThat(gateway.isHealthy()).isTrue();
            });
    }

    private ApplicationContextRunner ssrContext(String... properties) {
        return contextRunner
            .withPropertyValues("inertia.ssr.enabled=true", "inertia.ssr.url=" + serverUrl())
            .withPropertyValues(properties);
    }

    private ApplicationContextRunner processContext(Path bundle) {
        return ssrContext(
            "inertia.ssr.bundle=" + bundle,
            "inertia.ssr.process.enabled=true",
            "inertia.ssr.process.runtime=sh",
            "inertia.ssr.process.startup-timeout=200ms",
            "inertia.ssr.process.shutdown-timeout=200ms"
        );
    }

    private String serverUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
