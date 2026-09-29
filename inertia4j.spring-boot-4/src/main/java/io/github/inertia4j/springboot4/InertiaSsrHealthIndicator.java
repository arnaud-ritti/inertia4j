package io.github.inertia4j.springboot4;

import io.github.inertia4j.core.HttpSsrGateway;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Health of the Inertia server-side rendering server, reported as {@code inertiaSsr}: up when its {@code /health}
 * endpoint answers, or while the Vite dev server renders pages, down otherwise.
 */
public class InertiaSsrHealthIndicator implements HealthIndicator {
    private final HttpSsrGateway gateway;

    /**
     * @param gateway gateway reaching the server-side rendering server.
     */
    public InertiaSsrHealthIndicator(HttpSsrGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Health health() {
        String hotUrl = gateway.getHotUrl();

        if (hotUrl != null) {
            return Health.up().withDetail("url", hotUrl).withDetail("viteDevServer", true).build();
        }

        Health.Builder builder = gateway.isHealthy() ? Health.up() : Health.down();

        return builder.withDetail("url", gateway.getUrl()).build();
    }
}
