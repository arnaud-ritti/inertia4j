package io.github.inertia4j.springboot3;

import io.github.inertia4j.core.vite.Vite;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class InertiaSpringAutoconfigurationClasspathTest {
    @Test
    void context_whenSpringWebMvcIsMissing_starts() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InertiaSpringAutoconfiguration.class))
            .withClassLoader(new FilteredClassLoader("org.springframework.web.servlet"))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(Vite.class);
            });
    }

    @Test
    void context_whenSpringWebMvcIsPresent_registersViteAssetsConfigurer() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InertiaSpringAutoconfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("inertiaViteAssets");
                assertThat(context.getBean("inertiaViteAssets")).isInstanceOf(WebMvcConfigurer.class);
            });
    }
}
