package io.github.inertia4j.springshared.testing;

import io.github.inertia4j.core.testing.InertiaReloader;
import io.github.inertia4j.core.testing.ReloadRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Reloads Inertia pages through {@link MockMvc}, carrying over the session and cookies of the original request.
 * The request is built without {@code MockMvcRequestBuilders}, whose API differs between Spring 6 and Spring 7.
 */
@NullMarked
final class MockMvcReloader implements InertiaReloader {
    private final MockMvc mockMvc;
    private final MockHttpServletRequest originalRequest;

    MockMvcReloader(MockMvc mockMvc, MockHttpServletRequest originalRequest) {
        this.mockMvc = mockMvc;
        this.originalRequest = originalRequest;
    }

    @Override
    public String reload(ReloadRequest reloadRequest) {
        MvcResult result;

        try {
            result = mockMvc.perform(servletContext -> buildRequest(servletContext, reloadRequest)).andReturn();
        } catch (Exception exception) {
            throw new IllegalStateException("Inertia reload request to " + reloadRequest.getUrl() + " failed", exception);
        }

        return InertiaResultMatchers.pageBody(result);
    }

    private MockHttpServletRequest buildRequest(jakarta.servlet.ServletContext servletContext, ReloadRequest reloadRequest) {
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", reloadRequest.getPath());
        String query = reloadRequest.getQuery();

        request.setScheme(originalRequest.getScheme());
        request.setServerName(originalRequest.getServerName());
        request.setServerPort(originalRequest.getServerPort());
        request.setSecure(originalRequest.isSecure());
        request.setQueryString(query);
        addParameters(request, query);
        reloadRequest.getHeaders().forEach(request::addHeader);

        HttpSession session = originalRequest.getSession(false);

        if (session != null) {
            request.setSession(session);
        }

        Cookie[] cookies = originalRequest.getCookies();

        if (cookies != null) {
            request.setCookies(cookies);
        }

        return request;
    }

    private static void addParameters(MockHttpServletRequest request, @Nullable String query) {
        if (query == null || query.isEmpty()) {
            return;
        }

        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }

            int separatorIndex = pair.indexOf('=');
            String name = separatorIndex < 0 ? pair : pair.substring(0, separatorIndex);
            String value = separatorIndex < 0 ? "" : pair.substring(separatorIndex + 1);

            request.addParameter(decode(name), decode(value));
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
