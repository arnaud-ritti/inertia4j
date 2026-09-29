# Inertia4J Ktor

This document describes how to install and use Inertia4J with Ktor.

For a complete example, please refer to the [inertia4j-ktor-example](https://github.com/Inertia4J/inertia4j-ktor-example) repository.

## Installation

### Backend

Add the Ktor Inertia4J dependency to your project, via Gradle or Maven:

```kotlin
// build.gradle.kts
dependencies {
  implementation("io.github.inertia4j:inertia4j-ktor:2.0.0")
}
```

```xml
<!-- pom.xml -->
<dependencies>
    <dependency>
        <groupId>io.github.inertia4j</groupId>
        <artifactId>inertia4j-ktor</artifactId>
        <version>2.0.0</version>
    </dependency>
</dependencies>
```

### Frontend

Follow the [Client-side setup](https://inertiajs.com/docs/v3/installation/client-side-setup) guide for the client-side
configuration steps. Inertia4J 2.x implements the [Inertia.js v3 protocol](https://inertiajs.com/docs/v3/core-concepts/the-protocol);
use Inertia4J 1.x with older clients. Upgrading from 1.x? Read the [migration guide](../docs/migration-2.0.md).

## Usage

### Responses

To use Inertia4J with Ktor, you first need to import `io.github.inertia4j.ktor.Inertia` and
`io.github.inertia4j.ktor.inertia`.

In your `embeddedServer`, install the Inertia4J Ktor plugin:

```kotlin
embeddedServer(Netty, port = 8080) {
    install(Inertia)
    // ...
}
```

After installing, you can call `inertia.render()` instead of `call.respond()` in your Ktor routes.
The `render` function requires at least two arguments: the name of the component to be rendered in 
client-side, and the props to be passed to the component in the form of a `Pair<String, Any>`. These will be converted to a JSON object and sent to the client. Below is an example of this usage:

```kotlin
import io.github.inertia4j.ktor.Inertia
import io.github.inertia4j.ktor.inertia

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(Inertia)

        routing {
            get("/") {
                val recordRepository = RecordRepository()

                inertia.render("records/Index", "records" to recordRepository.all())
            }
        }
    }.start(wait = true)
}
```

This will instruct the frontend to render the `records/Index` component with a single prop called `records`, which contains the list of records, as retrieved from `RecordRepository`.

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

The template path and the root element id can be changed with the `templatePath` and `rootId` plugin settings.

To load your frontend, add `@Vite(src/main/frontend/main.tsx)@` to the template head (preceded by
`@ViteReactRefresh@` for React). It renders the tags of the Vite dev server while it runs and of the production
build otherwise. See the [Vite integration guide](../docs/vite.md).

### Options

The `render` function also supports the `encryptHistory` and `clearHistory` parameters. If you need more information about their functionality, you can read the
[official Inertia docs](https://inertiajs.com/docs/v3/security/history-encryption) on this topic.
Below is a usage example:

```kotlin
get("/") {
    val recordRepository = RecordRepository()

    inertia.render("records/Index", "records" to recordRepository.all(), encryptHistory = true, clearHistory = true)
}
```

This way, the response will be sent with the `encryptHistory` and `clearHistory` values set to `true`.
If you don't want to pass `encryptHistory` on every `render` call, you can provide a default value for it when installing the Inertia plugin:

```kotlin
fun main() {
    embeddedServer(Netty, port = 8080) {
        install(Inertia) {
            encryptHistory = true
        }
    }.start(wait = true)
}
```

It is important to note that it's not possible to change the default value of `clearHistory` the same way. If you want it
to be `true`, that needs to be specified on the `render` call, or set with `inertia.clearHistory()` before redirecting to
the page.

`render` also takes a `status`, e.g. to render an error page with Inertia:

```kotlin
inertia.render("Errors/NotFound", status = HttpStatusCode.NotFound)
```

### Asset Versioning

The Inertia4J adapter fully supports asset versioning and responds accordingly to requests with outdated assets.
When installing the Inertia plugin, the `versionProvider` property can be set to an implementation of the `VersionProvider` interface, which will be called on every request to compare the client's version with the provider's version. When the version changes, the server will return a response instructing the client to perform a full-page reload.
The version returned by the provider can be any string, and a common strategy is to generate a hash of the asset folder.
The `versionProvider` property is optional. By default, the version is the SHA-256 of the Vite manifest, so clients
reload after each deployment of a new frontend build (see the [Vite integration guide](../docs/vite.md)). Set it if
you don't build your frontend with Vite.

Below is an example of how to use a custom `versionProvider`:

```kotlin
/*
 * Reads a file containing a hex digest of the assets directory.
 * Usually, this file will be generated by a frontend build tool.
 */
private val assetVersion = {}::class.java
    .getResourceAsStream("assets.sha1")?.readAllBytes()?.let(::String)
    ?: error("Missing asset hex digest file")

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(Inertia) {
            versionProvider = { assetVersion }
        }
    }.start(wait = true)
}
```

### Redirecting

Inertia4J supports redirecting, and as the Inertia docs specify, there are two kinds of redirects. The first
is via the `redirect` method, and it is meant to redirect to other Inertia routes. The second kind of redirect is via
the `location` method, which redirects the client to a non-Inertia route in your application, or to an external route.
Both methods only receive a single parameter, which is the route to redirect to.

Below is an example of both methods being used:

```kotlin
embeddedServer(Netty, port = 8080) {
    install(Inertia)

    routing {
        get("/records") {
            /* ... */
        }

        post("/records") {
            /* ... */
            inertia.redirect("/records")
        }
            
        get("/external-redirect") {
            inertia.location("https://github.com/Inertia4J/inertia4j")
        }
    }
}.start(wait = true)
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

The same rules apply to redirects that don't go through `inertia.redirect`, such as `call.respondRedirect`: the plugin
turns a `302 Found` answering a `PUT`, `PATCH` or `DELETE` Inertia request into a `303 See Other`, and a redirect to a
location with a URL fragment into a `409 Conflict` with `X-Inertia-Redirect`. It also answers `GET` Inertia requests
sent with an outdated asset version before they reach your route, keeping flash data in the session, redirects an
Inertia request answered with an empty `200 OK` (e.g. `call.respond(HttpStatusCode.OK)`) back to its `Referer` (or `/`),
and adds `Vary: X-Inertia` to every response. Disable this with `middleware = false` in the plugin configuration.

### Partial Reloads

Inertia4J supports partial reloads, in case you don't need to return all the data to your client-side on component
load, or in case you just need to reload a specific component in your page. Only the requested props are resolved;
pass expensive props as functions so they are skipped when not requested. Nested props can be requested with dotted
paths (e.g. `only: ['auth.user']`).

```kotlin
inertia.render(
    "Users/Index",
    "users" to { userRepository.findAll() },                           // lazy: only resolved when sent
    "companies" to InertiaProps.optional { companyRepository.findAll() }, // never sent on full visits, only when requested
    "auth" to InertiaProps.always(currentUser),                          // always sent, even when not requested
)
```

See the [official docs](https://inertiajs.com/docs/v3/data-props/partial-reloads).

### Shared Data

Data needed by every page (the authenticated user, flash messages, the app name...) can be shared with all Inertia
responses through the plugin configuration:

```kotlin
install(Inertia) {
    share { call ->
        mapOf(
            "appName" to "My App",
            "user" to { currentUser(call) } // lazy, evaluated only when sent
        )
    }
}
```

Props can also be shared with the current call only:

```kotlin
inertia.share("flash", "Record saved!")
```

Dotted keys set nested props (`inertia.share("auth.user", user)`), and `inertia.shareOnce(key) { ... }` shares a
[once prop](#once-props). The top-level keys of shared props are listed in the page object, so the client carries them
over during instant visits; set `exposeSharedPropKeys = false` to disable it.

Props given to `render` take precedence over shared props when keys collide. Function prop values (`() -> T`) are lazy:
they are only evaluated when the prop is included in the response. See the
[official docs](https://inertiajs.com/docs/v3/data-props/shared-data).

### Deferred Props

Deferred props are left out of the initial page load, and fetched by the client right after the page renders. Props of
the same group are fetched in the same request:

```kotlin
inertia.render(
    "records/Index",
    "records" to recordRepository.findAll(),
    "permissions" to InertiaProps.defer { permissionRepository.findAll() },
    "teams" to InertiaProps.defer({ teamRepository.findAll() }, "attributes"),
    "projects" to InertiaProps.defer({ projectRepository.findAll() }, "attributes"),
)
```

A deferred prop that may fail can be rescued: the exception is reported (logged by default, see the
`exceptionReporter` setting), the other props are still sent, and the client renders the `rescue` slot of its
`<Deferred>` component:

```kotlin
"permissions" to InertiaProps.defer { permissionService.fetch() }.rescue()
```

Rescuing only applies to deferred props: `rescue()` has no effect on other props, whose exceptions propagate.

See the [official docs](https://inertiajs.com/docs/v3/data-props/deferred-props).

### Merging Props

By default, props returned by a partial reload replace the ones held by the client. Merge props are merged instead:

```kotlin
inertia.render(
    "records/Index",
    "records" to InertiaProps.merge(recordPage.items),                         // append items
    "notifications" to InertiaProps.merge(notifications).prepend(),            // prepend items
    "feed" to InertiaProps.merge(feed).append("data").prepend("messages"),     // merge nested arrays
    "posts" to InertiaProps.merge(posts).matchOn("id"),                        // update existing items in place
    "users" to InertiaProps.merge(users).append("data", "id"),                 // append to data, match on data.id
    "conversations" to InertiaProps.deepMerge(conversations).matchOn("data.id"), // merge nested objects recursively
)
```

Merging also works with deferred props: `InertiaProps.defer { ... }.merge()` or `InertiaProps.defer { ... }.deepMerge()`.
To make the value of a merge prop lazy, wrap it in a `Supplier { ... }`. Props reset by the client
(`router.reload({ reset: ['records'] })`) are sent without merge instructions. See the
[official docs](https://inertiajs.com/docs/v3/data-props/merging-props).

### Once Props

Once props are resolved a single time and remembered by the client, which reuses them on subsequent pages including
the same prop:

```kotlin
inertia.render(
    "Billing/Plans",
    "plans" to InertiaProps.once { planRepository.findAll() },
    "countries" to InertiaProps.once { countryRepository.findAll() }
        .key("countries")                   // share the remembered value across props of other pages
        .expiresIn(Duration.ofHours(1)),    // or .until(Instant)
)
```

Use `.fresh()` to send a new value even when the client remembers the prop. Once also combines with other prop types,
e.g. `InertiaProps.defer { ... }.once()`. See the [official docs](https://inertiajs.com/docs/v3/data-props/once-props).

### Infinite Scroll

Scroll props hold a page of items under a `data` key, merged with the items the client already holds, along with the
pagination state used by the `<InfiniteScroll>` component:

```kotlin
val page = postRepository.page(number, size)

inertia.render(
    "Posts/Index",
    "posts" to InertiaProps.scroll(mapOf("data" to page.items), ScrollMetadata.forPage(number, page.hasNext)),
)
```

Use `ScrollMetadata.of(pageName, previous, next, current)` for cursor pagination, `.wrapper("items")` when the items
are held under another key, and `InertiaProps.scroll({ ... }, { page -> metadata }).defer()` to load the first page after
the initial render. See the [official docs](https://inertiajs.com/docs/v3/data-props/infinite-scroll).

### Flash Data, Validation Errors and Redirect Flags

Flash data, validation errors and the `preserveFragment` and `clearHistory` flags are sent with the next rendered page,
typically after a redirect, including a page rendered for a prefetch request, as in the Laravel adapter. They are kept by an `InertiaFlashStore`; the default one uses the Ktor `Sessions` plugin,
with an `InertiaSession` registered:

```kotlin
install(Sessions) {
    cookie<InertiaSession>("INERTIA_SESSION", SessionStorageMemory()) {
        serializer = InertiaSession.Serializer
    }
}

routing {
    post("/users") {
        val errors = validate(call.receive<UserForm>())
        if (errors.isNotEmpty()) {
            inertia.errors(errors) // e.g. mapOf("name" to "The name field is required.")
            return@post inertia.back()
        }
        /* ... */
        inertia.flash("message", "User created")
        inertia.redirect("/users")
    }
}
```

Every page has an `errors` prop, empty by default, namespaced under the error bag requested by the client, if any. The
client exposes flash data through the `inertia:flash` event. Pass lists of messages to `inertia.errors` to send every
message of each field, and pair them with `errorValueType.set(ErrorValueType.StringArray)` in the
[TypeScript types](../docs/typescript.md) configuration. Set `flashStore` in the plugin configuration to keep this
data elsewhere. See the [flash data](https://inertiajs.com/docs/v3/data-props/flash-data) and
[validation](https://inertiajs.com/docs/v3/the-basics/validation) docs.

### Precognition

Precognition requests ask the server to validate a form without executing the action. Validate, then respond with
`inertia.precognition`, which returns `204 No Content` when the fields validated by the client have no errors and
`422 Unprocessable Entity` with the errors otherwise. Install the route-scoped `Precognition` plugin so responses carry
`Vary: Precognition`:

```kotlin
route("/users") {
    install(Precognition)
    post {
        val errors: Map<String, List<String>> = validate(call.receive<UserForm>())
        if (inertia.isPrecognitive) {
            return@post inertia.precognition(errors)
        }
        /* ... */
    }
}
```

See the [official docs](https://inertiajs.com/docs/v3/the-basics/forms#precognition).

### CSRF Protection

The Inertia client reads the `XSRF-TOKEN` cookie and sends it back in the `X-XSRF-TOKEN` header. Ktor has no built-in
token of this kind: set the cookie yourself and check the header on state-changing requests, or rely on the `CSRF`
plugin's origin checks. See the [official docs](https://inertiajs.com/docs/v3/security/csrf-protection).

### Server-Side Rendering

Full page loads can be pre-rendered by the Inertia Node.js SSR server:

```kotlin
install(Inertia) {
    ssr {
        enabled = true
        url = "http://127.0.0.1:13714"
        // Optional
        timeout = Duration.ofSeconds(2)
        except = listOf("admin/*")
        hotUrl = "http://localhost:5173"
        onFailure { failure -> log.warn(failure.toString()) }
    }
}
```

When rendering fails, the page falls back to client-side rendering after notifying `onFailure`, unless `throwOnError`
is set. Render requests time out after 10 seconds unless `timeout` is set. While the Vite dev server runs (its hot
file exists), pages are rendered through it instead, at `hotUrl` when set. `except` paths are matched with or without
leading slash. See the [Vite guide](../docs/vite.md#server-side-rendering) for the frontend setup and the
[official docs](https://inertiajs.com/docs/v3/advanced/server-side-rendering).

### Typed props

Props can also be described by classes annotated with `@InertiaPage`, `@InertiaShared` and `@InertiaForm`, and turned
into TypeScript types by the `io.github.inertia4j.typescript` Gradle plugin. `inertia.render(pageProps)` takes the
component name from `@InertiaPage` and sends the properties of the object; `InertiaProp` fields
(`InertiaProps.defer { ... }`, `InertiaProps.merge(...)`, …) and `() -> T` lazy values keep their behaviour:

```kotlin
@InertiaPage("Records/Index")
data class RecordsIndexProps(val records: List<Record>, val stats: InertiaProp<List<Stat>>)

inertia.render(RecordsIndexProps(records, InertiaProps.defer { stats() }))
```

`shareTyped { call -> AppShared(...) }` shares a typed object with every response. Set
`propertyNaming = PropertyNaming.Snake` in the plugin configuration to send snake_case keys, matching the Gradle
plugin's `propertyNaming` option. See [TypeScript types](/docs/typescript.md).
