package io.github.inertia4j.springshared;

import io.github.inertia4j.core.SimpleTemplateRenderer;
import io.github.inertia4j.core.TemplateRenderingException;
import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.core.vite.ViteConfig;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.TemplateRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Spring Boot auto-configuration for Inertia4j.
 * Sets up default beans for {@link AbstractInertia}, {@link VersionProvider},
 * {@link PageObjectSerializer}, {@link TemplateRenderer} and {@link Vite} if they are not
 * already present in the application context, and serves the Vite build output.
 */
public abstract class AbstractInertiaSpringAutoconfiguration {
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
        return new Vite(properties.vite.toViteConfig());
    }

    /**
     * Creates a default {@link VersionProvider} bean if one doesn't already exist.
     * It returns the Vite asset version, or "1" when the Vite integration is disabled.
     *
     * @param vite The Vite integration.
     * @return A default VersionProvider bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public VersionProvider versionProvider(Vite vite) {
        if (!properties.vite.enabled) {
            return () -> "1";
        }

        return vite::version;
    }

    /**
     * Creates a default {@link PageObjectSerializer} bean if one doesn't already exist.
     *
     * @return A default PageObjectSerializer bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public abstract PageObjectSerializer pageObjectSerializer();

    /**
     * Creates a default {@link TemplateRenderer} bean using {@link SimpleTemplateRenderer}
     * and the template path from {@link InertiaConfigurationProperties} if one doesn't already exist.
     *
     * @param vite The Vite integration, used unless disabled.
     * @return A default TemplateRenderer bean.
     * @throws TemplateRenderingException if the template file cannot be loaded.
     */
    @Bean
    @ConditionalOnMissingBean
    public TemplateRenderer templateRenderer(Vite vite) throws TemplateRenderingException {
        Vite enabledVite = properties.vite.enabled ? vite : null;

        return new SimpleTemplateRenderer(properties.templatePath, enabledVite);
    }

    /**
     * Serves the Vite build directory under the Vite public path with long-lived cache headers.
     *
     * @param vite The Vite integration.
     * @return A WebMvcConfigurer registering the resource handler.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "inertia.vite", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebMvcConfigurer inertiaViteAssets(Vite vite) {
        ViteConfig config = vite.getConfig();
        Duration cacheMaxAge = properties.vite.cacheMaxAge;

        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler(config.getPublicPath() + "**")
                    .addResourceLocations("classpath:/" + config.getBuildDirectory() + "/")
                    .setCacheControl(CacheControl.maxAge(cacheMaxAge).cachePublic().immutable());
            }
        };
    }
}
