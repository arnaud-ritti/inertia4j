package io.github.inertia4j.springboot3;

import io.github.inertia4j.core.DefaultPageObjectSerializer;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.TemplateRenderer;
import io.github.inertia4j.springshared.AbstractInertiaSpringAutoconfiguration;
import io.github.inertia4j.springshared.SharedDataProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

/**
 * Spring Boot 3 auto-configuration for Inertia4j.
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
        return new DefaultPageObjectSerializer();
    }
}
