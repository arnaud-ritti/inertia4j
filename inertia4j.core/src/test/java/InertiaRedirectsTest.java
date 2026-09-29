import dev.arkoder.inertia4j.core.InertiaRedirects;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InertiaRedirectsTest {
    @Test
    void backLocation_isTheRefererOfTheSameHost() {
        FakeHttpRequest request = new FakeHttpRequest("POST", Map.of("Referer", "https://example.com/users?page=2"));

        assertEquals("https://example.com/users?page=2", InertiaRedirects.backLocation(request));
    }

    @Test
    void backLocation_ignoresTheSchemeOfTheReferer() {
        FakeHttpRequest request = new FakeHttpRequest("POST", Map.of("Referer", "http://EXAMPLE.com/users"));

        assertEquals("http://EXAMPLE.com/users", InertiaRedirects.backLocation(request));
    }

    @Test
    void backLocation_isTheRootForARefererOfAnotherHost() {
        FakeHttpRequest request = new FakeHttpRequest("POST", Map.of("Referer", "https://evil.example.net/phishing"));

        assertEquals("/", InertiaRedirects.backLocation(request));
    }

    @Test
    void backLocation_isTheRootForARefererOfAnotherPort() {
        FakeHttpRequest request = new FakeHttpRequest("POST", Map.of("Referer", "https://example.com:8443/users"));

        assertEquals("/", InertiaRedirects.backLocation(request));
    }

    @Test
    void backLocation_isTheRootWithoutReferer() {
        assertEquals("/", InertiaRedirects.backLocation(new FakeHttpRequest("POST", Map.of())));
    }

    @Test
    void backLocation_isTheRootForAMalformedReferer() {
        FakeHttpRequest request = new FakeHttpRequest("POST", Map.of("Referer", "not a url"));

        assertEquals("/", InertiaRedirects.backLocation(request));
    }

    @Test
    void backLocation_keepsARelativeReferer() {
        FakeHttpRequest request = new FakeHttpRequest("POST", Map.of("Referer", "/form?step=2"));

        assertEquals("/form?step=2", InertiaRedirects.backLocation(request));
    }

    @Test
    void backLocation_isTheRootForAProtocolRelativeReferer() {
        FakeHttpRequest request = new FakeHttpRequest("POST", Map.of("Referer", "//evil.example.net/phishing"));

        assertEquals("/", InertiaRedirects.backLocation(request));
    }

}
