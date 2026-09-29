# Advanced Usage

This is a guide meant to help users extend Inertia4J and customize it.

## Serialization

In the standard implementation of Inertia4J, we provide a basic JSON serializer that uses the Jackson library to
convert data passed to our `render` call into JSON for the client. However, you may be using a different JSON
serialization method, and Inertia4J allows you to implement your own serializer, so that your project doesn't depend on
Jackson.

In order to understand how to implement your own serializer, it's important to understand how Inertia works internally.

In Inertia, the entity we respond with to the client (as JSON) in order to render the correct component with its data, is
called a Page Object. The Inertia specification provides a
[page object specification](https://inertiajs.com/docs/v3/core-concepts/the-protocol#the-page-object) in their documentation, so if you need
to understand more about the Page Object, you can read the Inertia docs. Inertia4J serializes this page object
internally in order to provide it as a JSON to the client, with the correct object representation of any data
type used in your project. In order to facilitate extending this serialization functionality, we've provided an
[interface](https://github.com/Inertia4J/inertia4j/blob/main/inertia4j.spi/src/main/java/io/github/inertia4j/spi/PageObjectSerializer.java),
which can be implemented according to your project needs, and will work out of the box when plugged into Inertia4J.

This interface defines only a single method, which is the `serialize` method. The function of this method is (as you
might expect) to take a `PageObject` and return a
`String` that represents the data in that object. If the serialization fails, it should throw a
`SerializationException`. If you need to know more about the internal representation of the Page Object in Inertia4J,
please read its
[implementation](https://github.com/Inertia4J/inertia4j/blob/main/inertia4j.spi/src/main/java/io/github/inertia4j/spi/PageObject.java).

Props are already resolved and filtered for partial reloads when the page object reaches the serializer. The
serializer must omit top-level metadata fields that are empty or `false` (e.g. `mergeProps`, `encryptHistory`), as the
client defaults absent fields, and must always write `component`, `props`, `url` and `version`. Once you do implement
your own serializer, you can plug it into Inertia4J.

In Spring, you can achieve this by implementing the `PageObjectSerializer` interface in a Spring Bean, which can be injected
into your Inertia4J Spring project. The interface implementation could be achieved through something like this:

```java
import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.PageObjectSerializer;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

@Component
@Primary
public class MyCustomPageObjectSerializer implements PageObjectSerializer {
    @Override
    public String serialize(PageObject pageObject) {
        /* ... */
    }
}
```

In Ktor, you can achieve this by passing it to the plugin installation:

```kotlin
install(Inertia) {
    serializer = MyCustomPageObjectSerializer()
}
```

## HTML template

When an Inertia page is first fetched, the server provides an HTML document embedding the page object in a
`<script data-page="app" type="application/json">` element, followed by the `<div id="app">` element the client-side
application is mounted on. To achieve this, Inertia4J provides a `TemplateRenderer` interface, to which we implement a
default template renderer replacing the `@InertiaHead@` and `@InertiaApp@` placeholders of a template file. You may also
implement your own renderer, e.g. to use a template engine.

The renderer interface also specifies a single method, `render`, in which it receives a `RenderedPage`, and returns the
HTML document. `RenderedPage.getBody()` holds the page object script element and the root element (or the markup
rendered by the SSR server replacing both), to insert in the `<body>`, and `RenderedPage.getHead()` holds the elements
rendered by the SSR server for the `<head>`, empty otherwise. Insert both as-is: the page object JSON is already escaped
so it cannot close the script element early.

In the default renderer, content placed between `@InertiaHead@` and `@EndInertiaHead@` is a fallback rendered when
`RenderedPage.getHead()` is empty, i.e. when the page is not server-side rendered, as with the `<Head>` slot fallback
of the [client adapters](https://inertiajs.com/docs/v3/the-basics/title-and-meta):

```html
<head>
  @InertiaHead@
    <title>My app</title>
    <meta name="description" content="Default description">
  @EndInertiaHead@
</head>
```

Placeholders are located in the template before any substitution, so SSR or page object content containing
placeholder text is never replaced.

When implementing a new Template Renderer, just make sure that it complies with the
[Inertia protocol specification](https://inertiajs.com/docs/v3/core-concepts/the-protocol).

In Spring, you can achieve this by implementing the `TemplateRenderer` interface in a Spring Bean, which can be injected
into your Inertia4J Spring project. The interface implementation could be achieved through something like this:

```java
import io.github.inertia4j.spi.RenderedPage;
import io.github.inertia4j.spi.TemplateRenderer;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

@Component
@Primary
public class MyCustomTemplateRenderer implements TemplateRenderer {
    @Override
    public String render(RenderedPage page) {
        /* ... */
    }
}
```

To change the Template Renderer to your implementation in Ktor, you can write the following:

```kotlin
install(Inertia) {
    templateRenderer = MyCustomTemplateRenderer()
}
```

### Vite tags in a custom renderer

Custom renderers render the Vite tags through the `Vite` instance, available as a Spring bean or created from a
`ViteConfig`:

```java
import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.spi.RenderedPage;
import io.github.inertia4j.spi.TemplateRenderer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class MyCustomTemplateRenderer implements TemplateRenderer {
    private final Vite vite;

    public MyCustomTemplateRenderer(Vite vite) {
        this.vite = vite;
    }

    @Override
    public String render(RenderedPage page) {
        String head = vite.reactRefreshTag() + vite.tags("src/main/frontend/main.tsx") + page.getHead();
        /* ... */
    }
}
```

`vite.version()` returns the matching Inertia asset version, `vite.asset(path)` the URL of a file processed by Vite,
and `vite.cspNonce()` the [CSP nonce](vite.md#content-security-policy-nonce) of the current request, which `tags()` and
`reactRefreshTag()` already add to their tags.

## Server-side rendering

Server-side rendering goes through the `SsrGateway` interface, whose `render` method receives the page object and its
JSON, and returns the rendered `RenderedPage`, or `null` to fall back to client-side rendering. The default
`HttpSsrGateway` calls the Inertia Node.js SSR server, and parses its responses with a `JsonReader` (Jackson by default).

In Spring, define an `SsrGateway` bean to replace the default one, or a `JsonReader` bean to parse responses without
Jackson. In Ktor, set it in the plugin configuration:

```kotlin
install(Inertia) {
    ssr {
        enabled = true
        gateway = MyCustomSsrGateway()
    }
}
```

Outside the adapters, `HttpSsrGateway.builder()` configures the server URL, the Vite dev server URL provider, the
timeout, the SSR `bundle` (renders are skipped while it is missing, unless `ensureBundleExists(false)`), and the
failure listener. The gateway also exposes `isHealthy()` (`GET /health`) and `shutdown()` (`/shutdown`), the building
blocks of the Laravel `inertia:check-ssr` and `inertia:stop-ssr` commands. `SsrServerProcess` runs the bundle as a
child process, as the adapters do when the process is enabled:

```java
HttpSsrGateway gateway = HttpSsrGateway.builder().bundle(Path.of("ssr/ssr.js")).build();
SsrServerProcess process = SsrServerProcess.builder(gateway, Path.of("ssr/ssr.js"))
    .runtime("node")
    .startupTimeout(Duration.ofSeconds(10))
    .build();

process.start(); // waits until /health answers; does nothing if a server already runs
process.stop();  // /shutdown, then destroys the process if it is still running
```

## Core renderer

Adapters delegate to the framework-agnostic `InertiaRenderer`, configured with `InertiaRenderer.builder(...)`: root
element id, SSR gateway and excluded paths, reporter of rescued deferred prop exceptions, clock of once prop expirations,
and exposure of shared prop keys. In Spring, define an `InertiaRenderer` bean to replace the one built from the
`inertia.*` properties.

Adapters for other frameworks should also run `InertiaRenderer.checkVersion(request)` before handling each request, and
pass the status and location of every redirect through `InertiaRedirects`, as the Spring `InertiaFilter` and the Ktor
plugin do.

## Testing

The Spring and Ktor adapters wrap `io.github.inertia4j.core.testing.AssertableInertia`, which works with any test
client. Build it from a response body, either the page object JSON or the HTML document holding it:

```java
AssertableInertia.fromResponseBody(body)
    .component("Users/Index")
    .has("users", 3, user -> user.where("name", "Jane"))
    .hasDeferredProp("permissions");
```

The body is parsed with `DefaultJsonReader` (Jackson 2), or with the `JsonReader` passed to
`fromResponseBody(body, jsonReader)`. To use `reload`, `reloadOnly`, `reloadExcept` and `loadDeferredProps`, also pass
an `InertiaReloader`: it sends a `GET` request to `ReloadRequest.getUrl()` with `ReloadRequest.getHeaders()` (the
`X-Inertia`, version and partial reload headers) and returns the response body.
