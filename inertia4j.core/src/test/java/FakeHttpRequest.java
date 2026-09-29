import io.github.inertia4j.core.HttpRequest;

import java.util.Map;

public class FakeHttpRequest implements HttpRequest {
    private final Map<String, String> headers;
    private final String method;
    private final String url;

    public FakeHttpRequest(String method, Map<String, String> headers) {
        this(method, "/page", headers);
    }

    public FakeHttpRequest(String method, String url, Map<String, String> headers) {
        this.method = method;
        this.url = url;
        this.headers = headers;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getFullUrl() {
        return "https://example.com" + url;
    }
}
