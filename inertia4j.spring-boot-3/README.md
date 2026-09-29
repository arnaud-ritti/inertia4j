# Inertia4J Spring Boot 3

This document describes how to install and use Inertia4J with Spring Boot 3.

For a complete example, please refer to the [inertia4j-spring-example](https://github.com/Inertia4J/inertia4j-spring-example) repository.

## Installation

### Backend

Add the Inertia4J dependency to your project, via Gradle or Maven:

```kotlin
// build.gradle.kts
dependencies {
  implementation("io.github.inertia4j:inertia4j-spring-boot-3:2.0.0")
}
```

```xml
<!-- pom.xml -->
<dependencies>
    <dependency>
        <groupId>io.github.inertia4j</groupId>
        <artifactId>inertia4j-spring-boot-3</artifactId>
        <version>2.0.0</version>
    </dependency>
</dependencies>
```

### Frontend

Follow Inertia's [Client-side setup](https://inertiajs.com/docs/v3/installation/client-side-setup) guide for the client-side
configuration steps. Inertia4J 2.x implements the [Inertia.js v3 protocol](https://inertiajs.com/docs/v3/core-concepts/the-protocol);
use Inertia4J 1.x with older clients. Upgrading from 1.x? Read the [migration guide](/docs/migration-2.0.md).

## Usage

### Responses

In your controller, the simplest way to use Inertia4J is to inject the `Inertia` bean. This bean will give you access to
the Inertia4J methods. To respond with an Inertia response in your controller method, you can call `inertia.render`.
The `render` method takes two arguments. The first argument is the name of the component to be rendered client-side, and
the second argument is a map, which will be converted to a JSON object and sent to the client. This method returns a
`ResponseEntity<String>` instance, so when using Inertia4J in a route, the return type of your method should always be
`ResponseEntity<String>`.

```java
public class RecordController {
    @Autowired
    private Inertia inertia; // Inertia4J bean injection

    @GetMapping("/records")
    public ResponseEntity<String> index() {
        RecordRepository recordRepository = new RecordRepository();
        Set<Record> records = recordRepository.getAllRecords();

        return inertia.render("Records/Index", Map.of("records", records));
    }
}

```

This will instruct the frontend to render the `Records/Index` component with a single prop called "records", which
contains the list of records, as retrieved from `RecordRepository`.

### The HTML Template

The first time an Inertia request is made to the server, the server will respond with an HTML document. Inertia4J
will automatically load the `resources/templates/app.html` file in your project and replace two placeholders:

- `@InertiaApp@` is replaced with the page object script element followed by the application root element,
  `<script data-page="app" type="application/json">…</script><div id="app"></div>`;
- `@InertiaHead@` is replaced with the `<head>` elements rendered by the [SSR server](#server-side-rendering), and is
  empty otherwise.

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <title>My app</title>
    @InertiaHead@
  </head>
  <body>
    @InertiaApp@
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

The template path and the root element id can be changed with the `inertia.template-path` and `inertia.root-id`
properties.

### Options

Inertia4J supports option passing on response. To enable option passing, first you need to import
`io.github.inertia4j.springboot3.Inertia.Options`. After importing, you can now use the `Options` class to pass options as
a third argument to `inertia.render`. The Inertia protocol defines two main flags which can be passed through options,
those are the `encryptHistory` and `clearHistory` flags. If you need more information about their functionality
you can read the [official Inertia docs](https://inertiajs.com/docs/v3/security/history-encryption). Here is an example of option
passing in the Inertia response:

```java
import io.github.inertia4j.springboot3.Inertia.Options;

@GetMapping("/records")
public ResponseEntity<String> index() {
    /* ... */
    return inertia.render("Records/Index", records, Options.clearHistory().encryptHistory());
}
```

This way, the response will be sent with the `encryptHistory` value set to `true`. Note that this is only applied for
the next render call, after that, Inertia will revert the flags back to their default values.

You may want to provide a default value to the `encryptHistory` flag, and this is also supported. All you need to do is
to add the following line to your `application.properties` file:

```text
inertia.encrypt-history=true
```

In this case, if you wanted to set the flag to `false` for a specific response, you could then specify that in the options:

```java
inertia.render("Records/Index", records, Options.encryptHistory(false));
```

The `clearHistory` option works the same way, except it's not possible to set a default value for it. To clear the
history on the page rendered after a redirect, call `inertia.clearHistory()` before redirecting.

Options also set the response status, e.g. to render an error page with Inertia:

```java
return inertia.render("Errors/NotFound", Map.of(), Options.status(404));
```

### Asset Versioning

The Inertia4J adapter fully supports asset versioning, and responds accordingly to requests with outdated assets. To provide a version
to your assets, you will need to provide an implementation of the `VersionProvider` interface as a Spring Bean. This interface has only
a single method, called `get`, which returns your asset version number as a `String`. You can implement the `get`
method to suit your project's needs, be it a value that manually changes, or a dynamic hash of your asset folder.

The `VersionProvider` bean is optional, with the default implementation returning a fixed string. However, it's important to note that this prevents the client from performing automatic full-page reloads, and after a deployment, your client-side code will be stale until the user performs a browser refresh. **It's highly recommended to provide a custom implementation to prevent this issue**.

Below is an example of a simple `VersionProvider` implementation in Spring:

```java
import io.github.inertia4j.springboot3.VersionProvider;
import org.springframework.stereotype.Component;

@Component
public class MyCustomVersionProvider implements VersionProvider {
    @Override
    public String get() {
        return "latest";
    }
}
```

### Redirecting

Inertia4J supports redirecting, and as the Inertia docs specify, there are two kinds of redirects. The first
is via the `redirect` method, and it is meant to redirect to other Inertia routes. The second kind of redirect is via
the `location` method, which redirects the client to a non-Inertia route in your application, or to an external route.
Both methods only receive a single parameter, which is the route to redirect to.

Below is an example of both methods being used:

 ```java
public class RecordController {
    @Autowired
    private Inertia inertia; // Inertia4J bean injection
  
    @GetMapping("/records")
    public ResponseEntity<String> index() {
        /* ... */
    }
  
    @PostMapping("/records")
    public ResponseEntity<String> create() {
        /* ... */
        return inertia.redirect("/records"); // This redirects to our index "/records" route.
    }
  
    @GetMapping("/external-redirect")
    public ResponseEntity<String> externalRedirect() {
        return inertia.location("https://github.com/Inertia4J/inertia4j"); // Redirects to an external route.
    }
}
 ```

`redirect` returns `303 See Other` after `PUT`, `PATCH` and `DELETE` requests, so the browser follows it with a `GET`.
When the location contains a URL fragment (e.g. `/records/1#comments`), Inertia requests receive a `409 Conflict` with
an `X-Inertia-Redirect` header instead, and the client visits the location with a fresh request. Call
`inertia.preserveFragment()` before redirecting to keep the fragment of the original request. `inertia.back()`
redirects to the `Referer` of the request.

Note that in the example provided, we've defined a `POST` route as well. This is the most common use case for
redirecting in a simple application, and the redirect methods (both `inertia.redirect` and `inertia.location`) work on
routes that receive requests of any HTTP methods. If you need more information about redirects in Inertia, please read
the [official docs](https://inertiajs.com/docs/v3/the-basics/redirects).

### Partial Reloads

Inertia4J supports partial reloads, in case you don't need to return all the data to your client-side when the
component loads, or in case you just need to reload a specific component in your page. Only the requested props are
resolved; wrap expensive props in a `Supplier` so they are skipped when not requested. Nested props can be requested with
dotted paths (e.g. `only: ['auth.user']`).

```java
return inertia.render("Users/Index", Map.of(
    "users", (Supplier<Object>) () -> userRepository.findAll(), // lazy: only resolved when sent
    "companies", Inertia.optional(() -> companyRepository.findAll()), // never sent on full visits, only when requested
    "auth", Inertia.always(currentUser) // always sent, even when not requested
));
```

See the [official docs](https://inertiajs.com/docs/v3/data-props/partial-reloads).

### Shared Data

Data needed by every page (the authenticated user, flash messages, the app name...) can be shared with all Inertia
responses. Every `SharedDataProvider` bean is picked up automatically:

```java
@Component
public class AppSharedData implements SharedDataProvider {
    @Override
    public Map<String, Object> share(HttpServletRequest request) {
        return Map.of(
            "appName", "My App",
            "user", (Supplier<Object>) () -> currentUser(request) // lazy, evaluated only when sent
        );
    }
}
```

Props can also be shared with the current request only, e.g. from a filter or interceptor:

```java
inertia.share("flash", "Record saved!");
```

Dotted keys set nested props (`inertia.share("auth.user", user)`), and `inertia.shareOnce(key, supplier)` shares a
[once prop](#once-props). The top-level keys of shared props are listed in the page object, so the client carries them
over during instant visits; set `inertia.expose-shared-prop-keys=false` to disable it.

Props given to `render` take precedence over shared props when keys collide. Any `Supplier` prop value is lazy: it is only
evaluated when the prop is included in the response. See the [official docs](https://inertiajs.com/docs/v3/data-props/shared-data).

### Deferred Props

Deferred props are left out of the initial page load, and fetched by the client right after the page renders. Props of
the same group are fetched in the same request:

```java
return inertia.render("records/Index", Map.of(
    "records", recordRepository.findAll(),
    "permissions", Inertia.defer(() -> permissionRepository.findAll()),
    "teams", Inertia.defer(() -> teamRepository.findAll(), "attributes"),
    "projects", Inertia.defer(() -> projectRepository.findAll(), "attributes")
));
```

A deferred prop that may fail can be rescued: the exception is logged, the other props are still sent, and the client
renders the `rescue` slot of its `<Deferred>` component:

```java
"permissions", Inertia.defer(() -> permissionService.fetch()).rescue()
```

See the [official docs](https://inertiajs.com/docs/v3/data-props/deferred-props).

### Merging Props

By default, props returned by a partial reload replace the ones held by the client. Merge props are merged instead:

```java
return inertia.render("records/Index", Map.of(
    "records", Inertia.merge(recordPage.getContent()),                  // append items
    "notifications", Inertia.merge(notifications).prepend(),            // prepend items
    "feed", Inertia.merge(feed).append("data").prepend("messages"),     // merge nested arrays
    "posts", Inertia.merge(posts).matchOn("id"),                        // update existing items in place
    "conversations", Inertia.deepMerge(conversations).matchOn("data.id") // merge nested objects recursively
));
```

Merging also works with deferred props: `Inertia.defer(() -> ...).merge()` or `Inertia.defer(() -> ...).deepMerge()`.
Props reset by the client (`router.reload({ reset: ['records'] })`) are sent without merge instructions. See the
[official docs](https://inertiajs.com/docs/v3/data-props/merging-props).

### Once Props

Once props are resolved a single time and remembered by the client, which reuses them on subsequent pages including
the same prop:

```java
return inertia.render("Billing/Plans", Map.of(
    "plans", Inertia.once(() -> planRepository.findAll()),
    "countries", Inertia.once(() -> countryRepository.findAll())
        .key("countries")                   // share the remembered value across props of other pages
        .expiresIn(Duration.ofHours(1))     // or .until(Instant)
));
```

Use `.fresh()` to send a new value even when the client remembers the prop. Once also combines with other prop types,
e.g. `Inertia.defer(() -> ...).once()`. See the [official docs](https://inertiajs.com/docs/v3/data-props/once-props).

### Infinite Scroll

Scroll props hold a page of items under a `data` key, merged with the items the client already holds, along with the
pagination state used by the `<InfiniteScroll>` component:

```java
Page<Post> page = postRepository.findAll(pageable);

return inertia.render("Posts/Index", Map.of(
    "posts", Inertia.scroll(
        Map.of("data", page.getContent()),
        ScrollMetadata.forPage(page.getNumber() + 1, page.hasNext())
    )
));
```

Use `ScrollMetadata.of(pageName, previous, next, current)` for cursor pagination, `.wrapper("items")` when the items
are held under another key, and `Inertia.scroll(() -> ..., page -> metadata).defer()` to load the first page after the
initial render. See the [official docs](https://inertiajs.com/docs/v3/data-props/infinite-scroll).

### Flash Data

Flash data is sent with the next rendered page, typically after a redirect, and exposed by the client through the
`inertia:flash` event:

```java
@PostMapping("/records")
public ResponseEntity<String> create() {
    /* ... */
    inertia.flash("message", "Record created");
    return inertia.redirect("/records");
}
```

Flash data, validation errors and the `preserveFragment` and `clearHistory` flags are kept in the HTTP session until a
page is rendered. See the [official docs](https://inertiajs.com/docs/v3/data-props/flash-data).

### Validation Errors

Every page has an `errors` prop, empty by default. Set errors before redirecting back to the form; they are sent with
the next rendered page, namespaced under the error bag requested by the client, if any:

```java
@PostMapping("/users")
public ResponseEntity<String> store(@Valid @ModelAttribute UserForm form, BindingResult result) {
    if (result.hasErrors()) {
        inertia.errors(result); // first message of each field
        return inertia.back();
    }
    /* ... */
}
```

`inertia.errors(Map)` accepts any messages, and `ValidationErrors.allMessages(result)` keeps every message of each
field. See the [official docs](https://inertiajs.com/docs/v3/the-basics/validation).

### Precognition

Precognition requests ask the server to validate a form without executing the action. Validate, then respond with
`inertia.precognition`, which returns `204 No Content` when the fields validated by the client have no errors and
`422 Unprocessable Entity` with the errors otherwise:

```java
@PostMapping("/users")
public ResponseEntity<String> store(@Valid @RequestBody UserForm form, BindingResult result) {
    if (inertia.isPrecognitive()) {
        return inertia.precognition(result);
    }
    /* ... */
}
```

Register the `PrecognitionFilter` for these routes so their responses carry `Vary: Precognition`. See the
[official docs](https://inertiajs.com/docs/v3/the-basics/forms#precognition).

### Server-Side Rendering

Full page loads can be pre-rendered by the Inertia Node.js SSR server. Enable it in `application.properties`:

```text
inertia.ssr.enabled=true
inertia.ssr.url=http://127.0.0.1:13714
# Optional
inertia.ssr.timeout=2s
inertia.ssr.except=/admin/*
inertia.ssr.hot-url=http://localhost:5173
inertia.ssr.throw-on-error=false
```

When rendering fails, the page falls back to client-side rendering and an `SsrRenderFailed` application event is
published. `hot-url` renders pages through the Vite development server instead. See the
[official docs](https://inertiajs.com/docs/v3/advanced/server-side-rendering).
