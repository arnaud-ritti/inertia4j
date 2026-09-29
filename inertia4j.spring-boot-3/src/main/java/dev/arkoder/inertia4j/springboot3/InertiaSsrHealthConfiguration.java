package dev.arkoder.inertia4j.springboot3;

import dev.arkoder.inertia4j.springshared.InertiaSsrServer;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@code inertiaSsr} health indicator when Spring Boot Actuator is on the classpath and server-side
 * rendering is enabled, unless {@code management.health.inertia-ssr.enabled} is {@code false}.
 * Kept apart from the main auto-configuration so that it is skipped, without loading Actuator classes, when Actuator
 * is not on the classpath.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {
    "org.springframework.boot.actuate.health.HealthIndicator",
    "org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator"
})
@ConditionalOnProperty(prefix = "inertia.ssr", name = "enabled", havingValue = "true")
public class InertiaSsrHealthConfiguration {
    // Nested so that its condition is only loaded once Actuator is known to be present, and not annotated with
    // @Configuration so that component scanning never registers it without the conditions of the enclosing class.
    @ConditionalOnEnabledHealthIndicator("inertia-ssr")
    static class EnabledHealthIndicatorConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "inertiaSsrHealthIndicator")
        InertiaSsrHealthIndicator inertiaSsrHealthIndicator(InertiaSsrServer inertiaSsrServer) {
            return new InertiaSsrHealthIndicator(inertiaSsrServer.getGateway());
        }
    }
}
