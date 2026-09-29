package io.github.inertia4j.springshared;

import io.github.inertia4j.core.HttpSsrGateway;
import io.github.inertia4j.core.InertiaRenderer;
import io.github.inertia4j.core.SimpleTemplateRenderer;
import io.github.inertia4j.core.TemplateRenderingException;
import io.github.inertia4j.spi.JsonReader;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.SsrGateway;
import io.github.inertia4j.spi.TemplateRenderer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * Base Spring Boot autoconfiguration for Inertia4j.
 * Sets up default beans for {@link VersionProvider}, {@link PageObjectSerializer}, {@link TemplateRenderer},
 * {@link InertiaRenderer} and, when {@code inertia.ssr.enabled} is set, {@link SsrGateway},
 * unless they are already defined in the application context.
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
     * Provides a default {@link VersionProvider} bean if none is defined.
     *
     * @return a default VersionProvider instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public VersionProvider versionProvider() {
        return () -> "1";
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
     * @return a SimpleTemplateRenderer instance configured with the template path from properties.
     * @throws TemplateRenderingException if the template file cannot be read.
     */
    @Bean
    @ConditionalOnMissingBean
    public TemplateRenderer templateRenderer() throws TemplateRenderingException {
        return new SimpleTemplateRenderer(properties.getTemplatePath());
    }

    /**
     * Provides the {@link SsrGateway} bean when server-side rendering is enabled. Failed renders are published as
     * {@link SsrRenderFailed} events.
     *
     * @param jsonReader     reader of the server responses.
     * @param eventPublisher publisher of failure events.
     * @return an HttpSsrGateway instance.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "inertia.ssr", name = "enabled", havingValue = "true")
    public SsrGateway ssrGateway(JsonReader jsonReader, ApplicationEventPublisher eventPublisher) {
        InertiaConfigurationProperties.Ssr ssr = properties.getSsr();

        return HttpSsrGateway.builder()
            .url(ssr.getUrl())
            .hotUrl(ssr.getHotUrl())
            .timeout(ssr.getTimeout())
            .throwOnError(ssr.isThrowOnError())
            .jsonReader(jsonReader)
            .onFailure(failure -> {
                logger.warn(failure);
                eventPublisher.publishEvent(new SsrRenderFailed(failure));
            })
            .build();
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
