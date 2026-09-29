package io.github.inertia4j.springboot4;

import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.annotations.InertiaShared;
import io.github.inertia4j.core.InertiaProp;
import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.springshared.InertiaConfigurationProperties;
import io.github.inertia4j.springshared.SharedDataProvider;
import io.github.inertia4j.springshared.TypedSharedDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TypedPropsTest {
    @InertiaPage("Records/Index")
    private record RecordsIndexProps(List<String> records, InertiaProp<List<Integer>> stats) {}

    @InertiaPage("Users/Show")
    private record UsersShowProps(String firstName) {}

    @InertiaShared
    private record AppShared(String appName) {}

    private record NotAPage(String name) {}

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/records");
        request.addHeader("X-Inertia", "true");
        request.addHeader("X-Inertia-Version", "1");
    }

    private Inertia inertia(List<SharedDataProvider> sharedDataProviders, PropertyNaming naming) {
        return new Inertia(
            () -> "1",
            new Jackson3PageObjectSerializer(),
            page -> "",
            () -> request,
            sharedDataProviders,
            naming
        );
    }

    @Test
    void render_withPageProps_usesAnnotatedComponentAndProps() {
        ResponseEntity<String> response = inertia(List.of(), PropertyNaming.Camel)
            .render(new RecordsIndexProps(List.of("a"), Inertia.defer(() -> List.of(1))));

        assertEquals(
            "{\"component\":\"Records/Index\",\"props\":{\"errors\":{},\"records\":[\"a\"]},\"url\":\"/records\",\"version\":\"1\","
                + "\"deferredProps\":{\"default\":[\"stats\"]},\"sharedProps\":[\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void render_withPagePropsAndOptions_appliesOptions() {
        ResponseEntity<String> response = inertia(List.of(), PropertyNaming.Camel)
            .render(new UsersShowProps("Miles"), Inertia.Options.clearHistory());

        assertEquals(
            "{\"component\":\"Users/Show\",\"props\":{\"errors\":{},\"firstName\":\"Miles\"},\"url\":\"/records\",\"version\":\"1\","
                + "\"clearHistory\":true,\"sharedProps\":[\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void render_withPropsWithoutInertiaPage_throws() {
        Inertia inertia = inertia(List.of(), PropertyNaming.Camel);

        assertThrows(IllegalArgumentException.class, () -> inertia.render(new NotAPage("x")));
    }

    @Test
    void render_withTypedSharedDataProvider_mergesSharedProps() {
        TypedSharedDataProvider appShared = request -> new AppShared("Inertia4J");

        ResponseEntity<String> response = inertia(List.of(appShared), PropertyNaming.Camel)
            .render(new UsersShowProps("Miles"));

        assertEquals(
            "{\"component\":\"Users/Show\",\"props\":{\"appName\":\"Inertia4J\",\"errors\":{},\"firstName\":\"Miles\"},\"url\":\"/records\","
                + "\"version\":\"1\",\"sharedProps\":[\"appName\",\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void render_withSnakeNaming_renamesPropsAndSharedProps() {
        TypedSharedDataProvider appShared = request -> new AppShared("Inertia4J");

        ResponseEntity<String> response = inertia(List.of(appShared), PropertyNaming.Snake)
            .render(new UsersShowProps("Miles"));

        assertEquals(
            "{\"component\":\"Users/Show\",\"props\":{\"app_name\":\"Inertia4J\",\"errors\":{},\"first_name\":\"Miles\"},\"url\":\"/records\","
                + "\"version\":\"1\",\"sharedProps\":[\"app_name\",\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void propertyNaming_bindsLowerCaseValue() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("inertia.property-naming", "snake")));

        InertiaConfigurationProperties properties = binder.bind("inertia", InertiaConfigurationProperties.class).get();

        assertEquals(PropertyNaming.Snake, properties.getPropertyNaming());
    }

    @Test
    void propertyNaming_defaultsToCamel() {
        assertEquals(PropertyNaming.Camel, new InertiaConfigurationProperties().getPropertyNaming());
    }
}
