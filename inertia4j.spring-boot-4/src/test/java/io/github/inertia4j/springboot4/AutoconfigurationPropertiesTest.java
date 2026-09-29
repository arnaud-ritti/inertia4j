package io.github.inertia4j.springboot4;

import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.springshared.InertiaConfigurationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

            assertTrue(body.contains("\"props\":{\"first_name\":\"Miles\"}"), body);
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
                body.contains("\"props\":{\"owner\":{\"first_name\":\"Miles\",\"home_address\":{\"street_name\":\"Main\"}}}"),
                body
            );
            assertTrue(body.contains("\"encryptHistory\":false,\"clearHistory\":false"), body);
        });
    }

    @Test
    void propertyNaming_defaultsToCamelInAutoconfiguredInertia() {
        currentRequest(true);

        contextRunner.run(context -> {
            String body = context.getBean(Inertia.class).render(new UsersShowProps("Miles")).getBody();

            assertTrue(body.contains("\"props\":{\"firstName\":\"Miles\"}"), body);
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
}
