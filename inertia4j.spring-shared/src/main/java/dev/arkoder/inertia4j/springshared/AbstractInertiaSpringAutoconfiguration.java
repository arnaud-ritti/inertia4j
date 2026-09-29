package dev.arkoder.inertia4j.springshared;

import dev.arkoder.inertia4j.core.HttpSsrGateway;
import dev.arkoder.inertia4j.core.InertiaRenderer;
import dev.arkoder.inertia4j.core.SimpleTemplateRenderer;
import dev.arkoder.inertia4j.core.SsrServerProcess;
import dev.arkoder.inertia4j.core.TemplateRenderingException;
import dev.arkoder.inertia4j.core.vite.Vite;
import dev.arkoder.inertia4j.spi.JsonReader;
import dev.arkoder.inertia4j.spi.PageObjectSerializer;
import dev.arkoder.inertia4j.spi.SsrGateway;
import dev.arkoder.inertia4j.spi.TemplateRenderer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

/**
 * Base Spring Boot autoconfiguration for Inertia4j.
 * Sets up default beans for {@link Vite}, {@link VersionProvider}, {@link PageObjectSerializer},
 * {@link TemplateRenderer}, {@link InertiaRenderer} and, when {@code inertia.ssr.enabled} is set, {@link SsrGateway}
 * and {@link InertiaSsrServer}, unless they are already defined in the application context.
 */
@EnableConfigurationProperties(InertiaConfigurationProperties.class)
public abstract class AbstractInertiaSpringAutoconfiguration {
    private static final Log logger = LogFactory.getLog(AbstractInertiaSpringAutoconfiguration.class);

    /**
     * Configuration properties for Inertia.
     */
    @Autowired
    protected InertiaConfigurationProperties properties;

    /**
     * Creates the {@link Vite} integration from the `inertia.vite.*` properties if one doesn't already exist.
     *
     * @return A default Vite bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public Vite vite() {
        return new Vite(properties.getVite().toViteConfig());
    }

    /**
     * Provides a default {@link VersionProvider} bean if none is defined.
     * It returns the Vite asset version, or "1" when the Vite integration is disabled.
     *
     * @param vite The Vite integration.
     * @return a default VersionProvider instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public VersionProvider versionProvider(Vite vite) {
        if (!properties.getVite().isEnabled()) {
            return () -> "1";
        }

        return vite::version;
    }

    /**
     * Provides the {@link PageObjectSerializer} bean if none is defined.
     *
     * @return a PageObjectSerializer instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public abstract PageObjectSerializer pageObjectSerializer();

    /**
     * Provides the {@link JsonReader} bean parsing server-side rendering responses if none is defined.
     *
     * @return a JsonReader instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public abstract JsonReader jsonReader();

    /**
     * Provides a default {@link TemplateRenderer} bean using {@link SimpleTemplateRenderer} if none is defined.
     *
     * @param vite The Vite integration, used unless disabled.
     * @return a SimpleTemplateRenderer instance configured with the template path from properties.
     * @throws TemplateRenderingException if the template file cannot be read.
     */
    @Bean
    @ConditionalOnMissingBean
    public TemplateRenderer templateRenderer(Vite vite) throws TemplateRenderingException {
        Vite enabledVite = properties.getVite().isEnabled() ? vite : null;

        return new SimpleTemplateRenderer(properties.getTemplatePath(), enabledVite);
    }

    /**
     * Provides the {@link SsrGateway} bean when server-side rendering is enabled. Failed renders are published as
     * {@link SsrRenderFailed} events.
     *
     * Pages are rendered by the Vite dev server while its hot file exists, at {@code inertia.ssr.hot-url} when set,
     * or at the URL of the hot file otherwise. Otherwise, pages are rendered client-side without contacting the server
     * while the {@code inertia.ssr.bundle} is missing, unless {@code inertia.ssr.ensure-bundle-exists} is false.
     *
     * @param jsonReader     reader of the server responses.
     * @param eventPublisher publisher of failure events.
     * @param vite           The Vite integration, followed unless disabled.
     * @return an HttpSsrGateway instance.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "inertia.ssr", name = "enabled", havingValue = "true")
    public SsrGateway ssrGateway(JsonReader jsonReader, ApplicationEventPublisher eventPublisher, Vite vite) {
        return httpSsrGatewayBuilder(vite, jsonReader)
            .throwOnError(properties.getSsr().isThrowOnError())
            .onFailure(failure -> {
                logger.warn(failure);
                eventPublisher.publishEvent(new SsrRenderFailed(failure));
            })
            .build();
    }

    /**
     * Provides the {@link InertiaSsrServer} bean when server-side rendering is enabled, running the server when
     * {@code inertia.ssr.process.enabled} is set and checking it on startup when {@code inertia.ssr.check-on-startup}
     * is set. Reaches the server with the {@link SsrGateway} bean when it is an {@link HttpSsrGateway}, or with a
     * gateway built from the {@code inertia.ssr.*} properties otherwise.
     *
     * @param ssrGateway server-side rendering gateway.
     * @param jsonReader reader of the server responses.
     * @param vite       The Vite integration, followed unless disabled.
     * @return an InertiaSsrServer instance.
     * @throws IllegalStateException if the process is enabled without {@code inertia.ssr.bundle}.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "inertia.ssr", name = "enabled", havingValue = "true")
    public InertiaSsrServer inertiaSsrServer(SsrGateway ssrGateway, JsonReader jsonReader, Vite vite) {
        InertiaConfigurationProperties.Ssr ssr = properties.getSsr();
        HttpSsrGateway gateway = ssrGateway instanceof HttpSsrGateway
            ? (HttpSsrGateway) ssrGateway
            : httpSsrGatewayBuilder(vite, jsonReader).build();

        if (!ssr.getProcess().isEnabled()) {
            return new InertiaSsrServer(gateway, null, ssr.isCheckOnStartup());
        }

        if (ssr.getBundle() == null) {
            throw new IllegalStateException("inertia.ssr.process.enabled requires inertia.ssr.bundle to be set");
        }

        InertiaConfigurationProperties.Ssr.Process process = ssr.getProcess();
        String workingDirectory = process.getWorkingDirectory();
        SsrServerProcess serverProcess = SsrServerProcess.builder(gateway, Path.of(ssr.getBundle()))
            .runtime(process.getRuntime())
            .arguments(process.getArguments())
            .workingDirectory(workingDirectory != null ? Path.of(workingDirectory) : null)
            .environment(process.getEnvironment())
            .startupTimeout(process.getStartupTimeout())
            .shutdownTimeout(process.getShutdownTimeout())
            .build();

        return new InertiaSsrServer(gateway, serverProcess, ssr.isCheckOnStartup());
    }

    /**
     * Creates a builder of {@link HttpSsrGateway} configured from the {@code inertia.ssr.*} properties, following the
     * Vite dev server unless the Vite integration is disabled.
     *
     * @param vite       The Vite integration.
     * @param jsonReader reader of the server responses.
     * @return a gateway builder.
     */
    protected HttpSsrGateway.Builder httpSsrGatewayBuilder(Vite vite, JsonReader jsonReader) {
        InertiaConfigurationProperties.Ssr ssr = properties.getSsr();
        HttpSsrGateway.Builder builder = HttpSsrGateway.builder();

        String hotUrl = ssr.getHotUrl();

        if (!properties.getVite().isEnabled()) {
            builder.hotUrl(hotUrl);
        } else if (hotUrl != null) {
            builder.hotUrl(() -> vite.isDevMode() ? hotUrl : null);
        } else {
            builder.hotUrl(vite::devServerUrlIfRunning);
        }

        String bundle = ssr.getBundle();

        return builder
            .url(ssr.getUrl())
            .timeout(ssr.getTimeout())
            .jsonReader(jsonReader)
            .bundle(bundle != null ? Path.of(bundle) : null)
            .ensureBundleExists(ssr.isEnsureBundleExists());
    }

    /**
     * Provides the core {@link InertiaRenderer} bean if none is defined.
     *
     * @param pageObjectSerializer serializer of page objects.
     * @param versionProvider      provider of the asset version.
     * @param templateRenderer     renderer of the HTML document.
     * @param ssrGateway           server-side rendering gateway, if enabled.
     * @return an InertiaRenderer instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public InertiaRenderer inertiaRenderer(
        PageObjectSerializer pageObjectSerializer,
        VersionProvider versionProvider,
        TemplateRenderer templateRenderer,
        ObjectProvider<SsrGateway> ssrGateway
    ) {
        InertiaRenderer.Builder builder = InertiaRenderer
            .builder(pageObjectSerializer, versionProvider::get, templateRenderer)
            .rootId(properties.getRootId())
            .exposeSharedPropKeys(properties.isExposeSharedPropKeys())
            .withoutSsr(properties.getSsr().getExcept().toArray(String[]::new))
            .exceptionReporter(exception -> logger.error("Rescued deferred prop failed to resolve", exception));

        ssrGateway.ifAvailable(builder::ssrGateway);

        return builder.build();
    }
}
