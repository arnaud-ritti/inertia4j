package io.github.inertia4j.springshared;

import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.core.vite.ViteConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the Vite build output in servlet web applications.
 * Kept apart from the main auto-configuration so that it is skipped, without loading Spring MVC classes,
 * when Spring MVC is not on the classpath.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "inertia.vite", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ViteAssetsConfiguration {
    /**
     * Serves the Vite build directory under the Vite public path with long-lived cache headers.
     *
     * @param vite The Vite integration.
     * @param properties The Inertia configuration properties.
     * @return A WebMvcConfigurer registering the resource handler.
     */
    @Bean
    public WebMvcConfigurer inertiaViteAssets(Vite vite, InertiaConfigurationProperties properties) {
        ViteConfig config = vite.getConfig();

        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler(config.getPublicPath() + "**")
                    .addResourceLocations("classpath:/" + config.getBuildDirectory() + "/")
                    .setCacheControl(CacheControl.maxAge(properties.getVite().getCacheMaxAge()).cachePublic().immutable());
            }
        };
    }
}
