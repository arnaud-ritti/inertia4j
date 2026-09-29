# Inertia4J Ktor

This document describes how to install and use Inertia4J with Ktor.

For a complete example, please refer to the [inertia4j-ktor-example](https://github.com/Inertia4J/inertia4j-ktor-example) repository.

## Installation

### Backend

Add the Ktor Inertia4J dependency to your project, via Gradle or Maven:

```kotlin
// build.gradle.kts
dependencies {
  implementation("io.github.inertia4j:inertia4j-ktor:1.0.3")
}
```

```xml
<!-- pom.xml -->
<dependencies>
    <dependency>
        <groupId>io.github.inertia4j</groupId>
        <artifactId>inertia4j-ktor</artifactId>
        <version>1.0.3</version>
    </dependency>
</dependencies>
```

### Frontend

Follow the [Client-side setup](https://inertiajs.com/client-side-setup) guide for the client-side configuration steps.

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
will automatically load the `resources/templates/app.html` file in your project and replace `@PageObject@` with the
data you wish to send to the client. If you wish to customize this template, just make sure to keep a div with id "app" and an HTML attribute `data-page='@PageObject@'`. Remember to use **single quotes** (i.e. `'@PageObject'`), given the JSON object will use double quotes.

To load your frontend, add `@Vite(src/main/frontend/main.tsx)@` to the template head (preceded by
`@ViteReactRefresh@` for React). It renders the tags of the Vite dev server while it runs and of the production
build otherwise. See the [Vite integration guide](../docs/vite.md).

### Options

The `render` function also supports the `encryptHistory` and `clearHistory` parameters. If you need more information about their functionality, you can read the
[official Inertia docs](https://inertiajs.com/history-encryption) on this topic.
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

It is important to note that it's not possible to change the default value of `clearHistory` the same way. If you want it to be `true`, that needs to be specified on the `render` call.

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

Note that in the example provided, we've defined a `POST` route as well. This is the most common use case for
redirecting in a simple application, and the redirect methods (both `inertia.redirect` and `inertia.location`) work on
routes that receive requests of any HTTP methods. If you need more information about redirects in Inertia, please read
the [official docs](https://inertiajs.com/redirects).

### Partial Reloads

Inertia4J also supports partial reloads, in case you don't need to return all the data to your client-side on component
load, or in case you just need to reload a specific component in your page.

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

Props given to `render` take precedence over shared props when keys collide. Function prop values (`() -> T`) are lazy:
they are only evaluated when the prop is included in the response. See the
[official docs](https://inertiajs.com/shared-data).

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

See the [official docs](https://inertiajs.com/deferred-props).

### Merging Props

By default, props returned by a partial reload replace the ones held by the client. Merge props are merged instead:

```kotlin
inertia.render(
    "records/Index",
    "records" to InertiaProps.merge(recordPage.items),                         // append items
    "notifications" to InertiaProps.merge(notifications).prepend(),            // prepend items
    "feed" to InertiaProps.merge(feed).append("data").prepend("messages"),     // merge nested arrays
    "posts" to InertiaProps.merge(posts).matchOn("id"),                        // update existing items in place
    "conversations" to InertiaProps.deepMerge(conversations).matchOn("data.id"), // merge nested objects recursively
)
```

Merging also works with deferred props: `InertiaProps.defer { ... }.merge()` or `InertiaProps.defer { ... }.deepMerge()`.
To make the value of a merge prop lazy, wrap it in a `Supplier { ... }`. Props reset by the client
(`router.reload({ reset: ['records'] })`) are sent without merge instructions. See the
[official docs](https://inertiajs.com/merging-props).
