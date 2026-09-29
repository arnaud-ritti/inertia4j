package io.github.inertia4j.springboot4;

import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.core.vite.ViteConfig;
import io.github.inertia4j.springshared.InertiaConfigurationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AutoconfigurationPropertiesTest {
    @InertiaPage("Users/Show")
    private record UsersShowProps(String firstName) {}

    private record Address(String streetName) {}

    private record Owner(String firstName, Address homeAddress) {}

    @InertiaPage("Owners/Show")
    private record OwnersShowProps(Owner owner) {}

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(InertiaSpringAutoconfiguration.class));

    private void currentRequest(boolean inertiaRequest) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/1");
        if (inertiaRequest) {
            request.addHeader("X-Inertia", "true");
            request.addHeader("X-Inertia-Version", "1");
        }

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void propertyNaming_snakeIsAppliedByAutoconfiguredInertia() {
        currentRequest(true);

        contextRunner.withPropertyValues("inertia.property-naming=snake").run(context -> {
            String body = context.getBean(Inertia.class).render(new UsersShowProps("Miles")).getBody();

            assertTrue(body.contains("\"props\":{\"errors\":{},\"first_name\":\"Miles\"}"), body);
        });
    }

    @Test
    void validationAllErrors_sendsEveryMessageOfEachField() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/1");
        request.addHeader("X-Inertia", "true");
        request.addHeader("X-Inertia-Version", "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        contextRunner.withPropertyValues("inertia.validation.all-errors=true").run(context -> {
            Inertia inertia = context.getBean(Inertia.class);
            Errors errors = new MapBindingResult(new LinkedHashMap<>(), "user");
            errors.rejectValue("name", "required", "The name field is required.");
            errors.rejectValue("name", "string", "The name must be a string.");
            inertia.errors(errors);

            String body = inertia.render("Users/Edit").getBody();

            assertTrue(
                body.contains("\"errors\":{\"name\":[\"The name field is required.\",\"The name must be a string.\"]}"),
                body
            );
        });
    }

    @Test
    void propertyNaming_snakeIsAppliedToNestedObjectsByAutoconfiguredSerializer() {
        currentRequest(true);

        contextRunner.withPropertyValues("inertia.property-naming=snake").run(context -> {
            String body = context.getBean(Inertia.class)
                .render(new OwnersShowProps(new Owner("Miles", new Address("Main"))))
                .getBody();

            assertTrue(
                body.contains("\"owner\":{\"first_name\":\"Miles\",\"home_address\":{\"street_name\":\"Main\"}}"),
                body
            );
            assertTrue(body.contains("\"version\":\"1\",\"sharedProps\":[\"errors\"]"), body);
        });
    }

    @Test
    void propertyNaming_defaultsToCamelInAutoconfiguredInertia() {
        currentRequest(true);

        contextRunner.run(context -> {
            String body = context.getBean(Inertia.class).render(new UsersShowProps("Miles")).getBody();

            assertTrue(body.contains("\"props\":{\"errors\":{},\"firstName\":\"Miles\"}"), body);
        });
    }

    @Test
    void encryptHistory_isBoundFromProperties() {
        contextRunner.withPropertyValues("inertia.encrypt-history=true").run(context ->
            assertTrue(context.getBean(InertiaConfigurationProperties.class).isEncryptHistory())
        );
    }

    @Test
    void templatePath_defaultsToAppTemplateWhenUnset() {
        currentRequest(false);

        contextRunner.run(context -> {
            assertEquals("templates/app.html", context.getBean(InertiaConfigurationProperties.class).getTemplatePath());

            String body = context.getBean(Inertia.class).render(new UsersShowProps("Miles")).getBody();

            assertTrue(body.contains("<title>App</title>"), body);
        });
    }

    @Test
    void vite_isBoundFromProperties() {
        contextRunner.withPropertyValues(
            "inertia.vite.enabled=false",
            "inertia.vite.hot-file=storage/custom.hot",
            "inertia.vite.build-directory=public/dist",
            "inertia.vite.manifest=public/dist/manifest.json",
            "inertia.vite.public-path=/dist/",
            "inertia.vite.cache-max-age=1h"
        ).run(context -> {
            InertiaConfigurationProperties.ViteProperties vite = context.getBean(InertiaConfigurationProperties.class)
                .getVite();

            assertFalse(vite.isEnabled());
            assertEquals("storage/custom.hot", vite.getHotFile());
            assertEquals("public/dist", vite.getBuildDirectory());
            assertEquals("public/dist/manifest.json", vite.getManifest());
            assertEquals("/dist/", vite.getPublicPath());
            assertEquals(Duration.ofHours(1), vite.getCacheMaxAge());

            ViteConfig config = context.getBean(Vite.class).getConfig();

            assertEquals(Path.of("storage/custom.hot"), config.getHotFile());
            assertEquals("public/dist", config.getBuildDirectory());
            assertEquals("public/dist/manifest.json", config.getManifestPath());
            assertEquals("/dist/", config.getPublicPath());
        });
    }

    @Test
    void vite_defaultsWhenUnset() {
        contextRunner.run(context -> {
            InertiaConfigurationProperties.ViteProperties vite = context.getBean(InertiaConfigurationProperties.class)
                .getVite();

            assertTrue(vite.isEnabled());
            assertEquals("vite.hot", vite.getHotFile());
            assertEquals("static/build", vite.getBuildDirectory());
            assertNull(vite.getManifest());
            assertEquals("/build/", vite.getPublicPath());
            assertEquals(Duration.ofDays(365), vite.getCacheMaxAge());
            assertEquals("static/build", context.getBean(Vite.class).getConfig().getBuildDirectory());
        });
    }
}
