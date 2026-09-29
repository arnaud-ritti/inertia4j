# Configuration reference

- [Spring Boot properties](#spring-boot-properties)
- [Spring Boot beans](#spring-boot-beans)
- [Ktor plugin settings](#ktor-plugin-settings)

## Spring Boot properties

All properties are prefixed with `inertia.` and bound by `InertiaConfigurationProperties`. Durations accept Spring's
formats (`10s`, `365d`, `PT2S`).

### General

| Property                          | Default              | Description                                                                                    |
|-----------------------------------|----------------------|------------------------------------------------------------------------------------------------|
| `inertia.template-path`           | `templates/app.html` | Classpath location of the [root template](../guides/responses.md#the-root-template)            |
| `inertia.root-id`                 | `app`                | Id of the element the client mounts on                                                         |
| `inertia.encrypt-history`         | `false`              | Default value of the [`encryptHistory`](../guides/responses.md#history-encryption) flag        |
| `inertia.expose-shared-prop-keys` | `true`               | List the keys of shared props in the page object's `sharedProps`                               |
| `inertia.property-naming`         | `camel`              | Naming of typed props and of objects inside props: `camel` or `snake`                          |
| `inertia.filter.enabled`          | `true`               | Register the [`InertiaFilter`](../guides/redirects.md#redirects-that-bypass-inertia)           |
| `inertia.validation.all-errors`   | `false`              | Send every message of each field when setting errors from a `BindingResult`                    |

### Vite

| Property                        | Default                                 | Description                                                                 |
|---------------------------------|-----------------------------------------|-----------------------------------------------------------------------------|
| `inertia.vite.enabled`          | `true`                                  | Enable Vite placeholders, the manifest-based asset version and asset serving |
| `inertia.vite.hot-file`         | `vite.hot`                              | File whose presence enables development mode                                |
| `inertia.vite.build-directory`  | `static/build`                          | Classpath directory of the build output                                     |
| `inertia.vite.manifest`         | `{build-directory}/.vite/manifest.json` | Classpath location of the manifest                                          |
| `inertia.vite.public-path`      | `/build/`                               | URL prefix of built files                                                   |
| `inertia.vite.cache-max-age`    | `365d`                                  | `max-age` of the `Cache-Control` header of built files                      |
| `inertia.vite.integrity-key`    | `integrity`                             | Manifest field holding SRI hashes; empty or `false` disables `integrity`    |
| `inertia.vite.nonce-attribute`  | `cspNonce`                              | Request attribute holding the CSP nonce; empty disables `nonce`             |

### Server-side rendering

| Property                                | Default                  | Description                                                                       |
|-----------------------------------------|--------------------------|-----------------------------------------------------------------------------------|
| `inertia.ssr.enabled`                   | `false`                  | Render first visits with the SSR server                                           |
| `inertia.ssr.url`                       | `http://127.0.0.1:13714` | URL of the SSR server                                                             |
| `inertia.ssr.hot-url`                   | URL in `vite.hot`        | URL of the Vite dev server rendering pages while it runs                          |
| `inertia.ssr.timeout`                   | `10s`                    | Timeout of render requests                                                        |
| `inertia.ssr.throw-on-error`            | `false`                  | Throw on failure instead of falling back to client-side rendering                 |
| `inertia.ssr.except`                    | none                     | Paths never server-rendered; `*` matches any characters                           |
| `inertia.ssr.bundle`                    | none                     | Path of the SSR bundle; while it is missing, pages render client-side             |
| `inertia.ssr.ensure-bundle-exists`      | `true`                   | Skip SSR while `bundle` is missing                                                |
| `inertia.ssr.check-on-startup`          | `false`                  | Log a warning when the SSR server is unreachable at startup                       |
| `inertia.ssr.process.enabled`           | `false`                  | Start and stop the SSR server with the application (requires `bundle`)            |
| `inertia.ssr.process.runtime`           | `node`                   | Program running the bundle (`node`, `bun`, an absolute path)                      |
| `inertia.ssr.process.arguments`         | none                     | Arguments passed to the runtime before the bundle                                 |
| `inertia.ssr.process.working-directory` | application's            | Working directory of the process                                                  |
| `inertia.ssr.process.environment.*`     | none                     | Environment variables added to the process                                        |
| `inertia.ssr.process.startup-timeout`   | `10s`                    | Time given to the server to become healthy                                        |
| `inertia.ssr.process.shutdown-timeout`  | `5s`                     | Time given to the server to exit before its process is destroyed                  |

With Spring Boot Actuator, `management.health.inertia-ssr.enabled=false` disables the `inertiaSsr` health indicator.

## Spring Boot beans

The auto-configuration backs off when you define your own bean of these types:

| Bean type              | Default                                              | Replace it to                                                   |
|------------------------|------------------------------------------------------|-----------------------------------------------------------------|
| `VersionProvider`      | Vite manifest hash                                   | Compute the [asset version](../guides/asset-versioning.md)      |
| `PageObjectSerializer` | Jackson (2 on Boot 3; 3 on Boot 4, falling back to 2) | [Serialize page objects](../advanced.md#serialization) differently |
| `TemplateRenderer`     | `SimpleTemplateRenderer` on `template-path`          | Use a [template engine](../advanced.md#html-template)           |
| `SsrGateway`           | `HttpSsrGateway`                                     | [Render pages another way](../advanced.md#server-side-rendering) |
| `JsonReader`           | Jackson                                              | Parse SSR responses without Jackson                             |
| `Vite`                 | Built from `inertia.vite.*`                          | Resolve frontend tags differently                               |
| `InertiaRenderer`      | Built from `inertia.*`                               | Configure the [core renderer](../advanced.md#core-renderer)     |
| `InertiaFilter`        | Registered for every request                         | Change the filter's order or URL patterns                       |
| `Inertia`              | Built from the beans above                           | Rarely needed                                                   |

Beans picked up from the context:

| Bean type                 | Effect                                                   |
|---------------------------|----------------------------------------------------------|
| `SharedDataProvider`      | Adds [shared props](../guides/shared-data.md)            |
| `TypedSharedDataProvider` | Adds typed shared props                                  |

## Ktor plugin settings

```kotlin
install(Inertia) {
    // settings below
}
```

### General

| Setting                | Type                              | Default                 | Description                                                                          |
|------------------------|-----------------------------------|-------------------------|--------------------------------------------------------------------------------------|
| `templatePath`         | `String`                          | `templates/app.html`    | Classpath location of the root template                                              |
| `rootId`               | `String`                          | `app`                   | Id of the element the client mounts on                                               |
| `encryptHistory`       | `Boolean`                         | `false`                 | Default value of the `encryptHistory` flag                                           |
| `exposeSharedPropKeys` | `Boolean`                         | `true`                  | List the keys of shared props in `sharedProps`                                       |
| `propertyNaming`       | `PropertyNaming`                  | `Camel`                 | Naming of typed props and of objects inside props                                    |
| `middleware`           | `Boolean`                         | `true`                  | Apply the protocol to every call (version check, 303, fragments, `Vary`)             |
| `versionProvider`      | `(() -> String)?`                 | Vite version            | Computes the asset version                                                           |
| `serializer`           | `PageObjectSerializer?`           | Jackson 2               | Serializes page objects                                                              |
| `templateRenderer`     | `TemplateRenderer?`               | `SimpleTemplateRenderer` | Renders the root template                                                           |
| `flashStore`           | `InertiaFlashStore?`              | `SessionsFlashStore`    | Keeps flash data, errors and redirect flags between requests                         |
| `exceptionReporter`    | `((RuntimeException) -> Unit)?`   | Logs                    | Reports exceptions of rescued deferred props                                         |
| `share { call -> }`    | function                          |                         | Adds shared props                                                                    |
| `shareTyped { call -> }` | function                        |                         | Adds typed shared props                                                              |

### `vite { }`

| Setting        | Type                             | Default                                  | Description                                          |
|----------------|----------------------------------|------------------------------------------|------------------------------------------------------|
| `hotFile`      | `Path`                           | `vite.hot`                               | File whose presence enables development mode         |
| `buildDirectory` | `String`                       | `static/build`                           | Classpath directory of the build output              |
| `manifestPath` | `String?`                        | `$buildDirectory/.vite/manifest.json`    | Classpath location of the manifest                   |
| `publicPath`   | `String`                         | `/build/`                                | URL prefix of built files                            |
| `serveAssets`  | `Boolean`                        | `true`                                   | Serve the build directory under `publicPath`         |
| `cacheMaxAge`  | `kotlin.time.Duration`           | `365.days`                               | `max-age` of built files                             |
| `integrityKey` | `String?`                        | `integrity`                              | Manifest field holding SRI hashes; `null` disables   |
| `nonce`        | `((ApplicationCall) -> String?)?` | `null`                                  | Resolves the CSP nonce of a call                     |

### `ssr { }`

| Setting              | Type                    | Default                  | Description                                                        |
|----------------------|-------------------------|--------------------------|--------------------------------------------------------------------|
| `enabled`            | `Boolean`               | `false`                  | Render first visits with the SSR server                            |
| `url`                | `String`                | `http://127.0.0.1:13714` | URL of the SSR server                                              |
| `hotUrl`             | `String?`               | URL in `vite.hot`        | URL of the Vite dev server rendering pages while it runs           |
| `timeout`            | `java.time.Duration?`   | 10 seconds               | Timeout of render requests; `null` for none                        |
| `throwOnError`       | `Boolean`               | `false`                  | Throw on failure instead of falling back to client-side rendering  |
| `except`             | `List<String>`          | empty                    | Paths never server-rendered                                        |
| `bundle`             | `Path?`                 | `null`                   | Path of the SSR bundle                                             |
| `ensureBundleExists` | `Boolean`               | `true`                   | Skip SSR while `bundle` is missing                                 |
| `checkOnStartup`     | `Boolean`               | `false`                  | Log a warning when the SSR server is unreachable at startup        |
| `jsonReader`         | `JsonReader?`           | Jackson 2                | Parses SSR responses                                               |
| `gateway`            | `SsrGateway?`           | `HttpSsrGateway`         | Replaces the HTTP gateway                                          |
| `onFailure { }`      | function                | logs a warning           | Notified of failed renders                                         |

### `ssr { process { } }`

| Setting            | Type                  | Default        | Description                                                   |
|--------------------|-----------------------|----------------|---------------------------------------------------------------|
| `enabled`          | `Boolean`             | `false`        | Start and stop the SSR server with the application            |
| `runtime`          | `String`              | `node`         | Program running the bundle                                    |
| `arguments`        | `List<String>`        | empty          | Arguments passed before the bundle                            |
| `workingDirectory` | `Path?`               | application's  | Working directory of the process                              |
| `environment`      | `Map<String, String>` | empty          | Environment variables added to the process                    |
| `startupTimeout`   | `java.time.Duration`  | 10 seconds     | Time given to the server to become healthy                    |
| `shutdownTimeout`  | `java.time.Duration`  | 5 seconds      | Time given to the server to exit before it is destroyed       |
