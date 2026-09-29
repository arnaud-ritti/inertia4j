# Server-side rendering

With server-side rendering (SSR), first visits are rendered to HTML by the Inertia Node.js SSR server, so the page is
visible before JavaScript loads and crawlers see its content. Inertia4J posts the page object to the SSR server and
inserts the returned markup into the [root template](responses.md#the-root-template): the body replaces
`@InertiaApp@`, the head elements replace `@InertiaHead@`. Inertia visits are unaffected: they still return JSON.

The frontend side (the SSR entry and the `@inertiajs/vite` plugin) is described in the
[Vite guide](vite.md#server-side-rendering).

## Enabling SSR

**Spring Boot** (`application.properties`):

```properties
inertia.ssr.enabled=true
inertia.ssr.url=http://127.0.0.1:13714
inertia.ssr.bundle=build/ssr/ssr.js
```

**Ktor**:

```kotlin
install(Inertia) {
    ssr {
        enabled = true
        url = "http://127.0.0.1:13714"
        bundle = Path.of("build/ssr/ssr.js")
    }
}
```

## How rendering behaves

| Situation                                        | Result                                                                   |
|--------------------------------------------------|--------------------------------------------------------------------------|
| Vite dev server running (`vite.hot` exists)      | Rendered by the dev server's `/__inertia_ssr` endpoint (or `hot-url`)    |
| `bundle` set and the file is missing             | Client-side rendering, without contacting the server or reporting failure |
| Path matches `except`                            | Client-side rendering                                                    |
| SSR server fails or times out                    | Client-side rendering, failure reported                                  |
| Failure with `throw-on-error` enabled            | Exception propagated                                                     |

Render requests time out after 10 seconds unless `timeout` is set. `except` patterns are relative to the application,
with or without a leading slash, and `*` matches any sequence of characters (`admin/*`).

Failures are reported as an `SsrRenderFailed` application event in Spring, and passed to `onFailure` in Ktor (logged as
warnings by default):

```java
@EventListener
void onSsrFailure(SsrRenderFailed event) {
    metrics.counter("ssr.failures").increment();
}
```

```kotlin
ssr {
    enabled = true
    onFailure { failure -> log.warn("SSR failed: {}", failure) }
}
```

## Health checks

- `inertia.ssr.check-on-startup=true` / `checkOnStartup = true` logs a warning when the SSR server is unreachable at
  startup.
- With Spring Boot Actuator, the `inertiaSsr` health indicator reports whether the server answers on `/health`.
  Disable it with `management.health.inertia-ssr.enabled=false`.

Both are skipped, or report up, while the Vite dev server renders pages.

## Running the SSR server from the application

Instead of running `node build/ssr/ssr.js` next to your application, Inertia4J can manage the process: it starts
`<runtime> [arguments...] <bundle>` on startup, waits until the server is healthy, and stops it through `/shutdown` on
shutdown (destroying the process if it doesn't exit in time). It doesn't start while the Vite dev server runs, nor
when a server already answers at the configured URL.

```properties
inertia.ssr.bundle=build/ssr/ssr.js
inertia.ssr.process.enabled=true
inertia.ssr.process.runtime=node
inertia.ssr.process.arguments=--enable-source-maps
inertia.ssr.process.environment.NODE_ENV=production
inertia.ssr.process.startup-timeout=10s
inertia.ssr.process.shutdown-timeout=5s
```

```kotlin
ssr {
    enabled = true
    bundle = Path.of("build/ssr/ssr.js")
    process {
        enabled = true
        runtime = "node"
        arguments = listOf("--enable-source-maps")
        environment = mapOf("NODE_ENV" to "production")
    }
}
```

`bundle` is required when the process is enabled.

## Hydration

When SSR is unavailable the client receives an empty root element, so the entry must choose between hydrating and
mounting:

```tsx
setup({ el, App, props }) {
  if (el.hasAttribute('data-server-rendered')) {
    hydrateRoot(el, <App {...props} />)
    return
  }

  createRoot(el).render(<App {...props} />)
}
```

## Custom gateway

To render pages some other way (another runtime, a remote service), replace the `SsrGateway`. See
[Extending Inertia4J](extending.md#server-side-rendering). All properties are listed in the
[configuration reference](../reference/configuration.md#server-side-rendering).

See the [Inertia documentation](https://inertiajs.com/docs/v3/advanced/server-side-rendering).
