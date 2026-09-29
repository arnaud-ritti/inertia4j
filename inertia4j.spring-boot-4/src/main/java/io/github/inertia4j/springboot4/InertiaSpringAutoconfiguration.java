package io.github.inertia4j.springboot4;

import io.github.inertia4j.core.DefaultPageObjectSerializer;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.TemplateRenderer;
import io.github.inertia4j.springshared.AbstractInertia;
import io.github.inertia4j.springshared.AbstractInertiaSpringAutoconfiguration;
import io.github.inertia4j.springshared.SharedDataProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

/**
 * Spring Boot 4 implementation of {@link AbstractInertiaSpringAutoconfiguration}.
 */
@Configuration
public class InertiaSpringAutoconfiguration extends AbstractInertiaSpringAutoconfiguration {
    @Override
    @Bean
    @ConditionalOnMissingBean
    public VersionProvider versionProvider() {
        return super.versionProvider()::get;
    }

    @Bean
    @ConditionalOnMissingBean
    public Inertia inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer,
        ObjectProvider<SharedDataProvider> sharedDataProviders
    ) {
        return new Inertia(
            versionProvider,
            pageObjectSerializer,
            templateRenderer,
            sharedDataProviders.orderedStream().collect(Collectors.toList()),
            properties.getPropertyNaming()
        );
    }

    @Override
    @Bean
    @ConditionalOnMissingBean
    public PageObjectSerializer pageObjectSerializer() {
        try {
            Class.forName("tools.jackson.databind.ObjectMapper");
            return new Jackson3PageObjectSerializer();
        } catch (ClassNotFoundException e) {
            return new DefaultPageObjectSerializer();
        }
    }
}
