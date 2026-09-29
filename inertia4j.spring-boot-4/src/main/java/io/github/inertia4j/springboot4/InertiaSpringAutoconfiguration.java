package io.github.inertia4j.springboot4;

import io.github.inertia4j.core.DefaultJsonReader;
import io.github.inertia4j.core.DefaultPageObjectSerializer;
import io.github.inertia4j.core.InertiaRenderer;
import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.spi.JsonReader;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.springshared.AbstractInertiaSpringAutoconfiguration;
import io.github.inertia4j.springshared.InertiaFilterConfiguration;
import io.github.inertia4j.springshared.SharedDataProvider;
import io.github.inertia4j.springshared.ViteAssetsConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.stream.Collectors;

/**
 * Spring Boot 4 autoconfiguration for Inertia4j. Uses Jackson 3 when available, Jackson 2 otherwise.
 */
@Configuration
@Import({ViteAssetsConfiguration.class, InertiaFilterConfiguration.class})
public class InertiaSpringAutoconfiguration extends AbstractInertiaSpringAutoconfiguration {
    @Override
    @Bean
    @ConditionalOnMissingBean
    public VersionProvider versionProvider(Vite vite) {
        return super.versionProvider(vite)::get;
    }

    /**
     * Provides the {@link Inertia} bean if none is defined.
     *
     * @param inertiaRenderer     core renderer.
     * @param sharedDataProviders providers of data shared with all responses.
     * @return an Inertia instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public Inertia inertia(
        InertiaRenderer inertiaRenderer,
        ObjectProvider<SharedDataProvider> sharedDataProviders
    ) {
        Inertia inertia = new Inertia(
            inertiaRenderer,
            sharedDataProviders.orderedStream().collect(Collectors.toList()),
            properties.getPropertyNaming()
        );
        inertia.setDefaultOptions(Inertia.Options.encryptHistory(properties.isEncryptHistory()));
        inertia.setAllErrors(properties.getValidation().isAllErrors());

        return inertia;
    }

    @Override
    @Bean
    @ConditionalOnMissingBean
    public PageObjectSerializer pageObjectSerializer() {
        if (isJackson3Present()) {
            return new Jackson3PageObjectSerializer(properties.getPropertyNaming());
        }

        return new DefaultPageObjectSerializer(properties.getPropertyNaming());
    }

    @Override
    @Bean
    @ConditionalOnMissingBean
    public JsonReader jsonReader() {
        if (isJackson3Present()) {
            return new Jackson3JsonReader();
        }

        return new DefaultJsonReader();
    }

    private static boolean isJackson3Present() {
        try {
            Class.forName("tools.jackson.databind.ObjectMapper");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
