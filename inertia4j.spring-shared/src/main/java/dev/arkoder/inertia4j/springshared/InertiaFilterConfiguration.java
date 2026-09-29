package dev.arkoder.inertia4j.springshared;

import dev.arkoder.inertia4j.core.InertiaRenderer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link InertiaFilter} in servlet web applications, unless {@code inertia.filter.enabled} is
 * {@code false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "inertia.filter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InertiaFilterConfiguration {
    /**
     * @param inertiaRenderer core renderer.
     * @return the Inertia filter, applied to every request.
     */
    @Bean
    @ConditionalOnMissingBean
    public InertiaFilter inertiaFilter(InertiaRenderer inertiaRenderer) {
        return new InertiaFilter(inertiaRenderer);
    }
}
