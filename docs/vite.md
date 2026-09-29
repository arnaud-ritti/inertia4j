# Vite integration

Inertia4J follows [Vite's backend integration guide](https://vite.dev/guide/backend-integration.html): the root
template loads your frontend from the Vite dev server while it runs, and from the production build otherwise.
The Inertia asset version is derived from the build, so browsers reload after each deployment.

## Setup

### 1. Configure Vite

`vite.config.ts` at the root of your project (the working directory of the backend):

```ts
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import fs from 'node:fs'

const hotFile = 'vite.hot'

function inertia4jHotFile(): Plugin {
  return {
    name: 'inertia4j-hot-file',
    apply: 'serve',
    configureServer(server) {
      server.httpServer?.once('listening', () => {
        fs.writeFileSync(hotFile, server.resolvedUrls!.local[0])
      })
      const clean = () => fs.rmSync(hotFile, { force: true })
      process.on('exit', clean)
      process.on('SIGINT', () => process.exit())
      process.on('SIGTERM', () => process.exit())
    },
  }
}

export default defineConfig(({ command }) => ({
  plugins: [react(), inertia4jHotFile()],
  base: command === 'build' ? '/build/' : '/',
  build: {
    manifest: true,
    outDir: 'src/main/resources/static/build',
    emptyOutDir: true,
    rollupOptions: { input: 'src/main/frontend/main.tsx' },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    origin: 'http://127.0.0.1:5173',
    cors: { origin: 'http://localhost:8080' },
  },
}))
```

- `inertia4jHotFile` writes the dev server URL to `vite.hot` while `vite` runs; Inertia4J switches to dev mode
  when the file exists.
- `base` applies to the build only, matching the URL prefix under which Inertia4J serves built files.
- `server.cors.origin` must be the origin of your backend.
- `strictPort` makes Vite fail instead of moving to another port, since `server.origin` must match the dev server URL; change `server.port` and `server.origin` together.
- `server.host` binds the dev server to an IPv4 address, which the backend reaches when it renders pages through the
  dev server (see [Troubleshooting](#troubleshooting)).

### 2. Start the entry with the module preload polyfill

```ts
import 'vite/modulepreload-polyfill'
```

### 3. Reference the entry in the template

`src/main/resources/templates/app.html`:

```html
<head>
  @ViteReactRefresh@
  @Vite(src/main/frontend/main.tsx)@
  @InertiaHead@
</head>
<body>
  @InertiaApp@
</body>
```

- `@Vite(a, b)@` renders the tags of one or more entries, given as paths relative to the Vite root.
- `@ViteReactRefresh@` renders the React Fast Refresh preamble in dev mode; omit it for Vue, Svelte and other
  frameworks.
- `@ViteAsset(src/images/logo.png)@` renders the URL of a file processed by Vite: the dev server URL in dev mode, the
  hashed file of the manifest in production (see [Static assets](#static-assets)).
- `@InertiaHead@...@EndInertiaHead@` renders its content, such as a default `<title>`, only when the page is not
  server-side rendered, and the SSR `<head>` elements in its place otherwise.

Use a single `@Vite(...)@` placeholder listing all entries: each placeholder renders independently, so several
placeholders would load `@vite/client` twice.

### 4. Ignore generated files

```
vite.hot
/src/main/resources/static/build/
```

## Development

Run `vite` and your application side by side. Pages load `@vite/client` and your entries from the dev server, with
hot module replacement. The asset version is `dev`.

## Production

Run `vite build` before packaging (for example from a Gradle task, as in the
[example application](../examples/spring-boot-react)). Pages load the hashed files listed in
`.vite/manifest.json` with their stylesheets and `modulepreload` hints. Files are served under `/build/` with
`Cache-Control: public, max-age=31536000, immutable`. The asset version is the SHA-256 of the manifest.

Every file under the build directory is served as immutable, so keep unhashed files out of it: set `publicDir: false`
in `vite.config.ts`, or serve Vite's `public/` files elsewhere. Exclude `vite.hot` from Docker images
(`.dockerignore`), otherwise production renders dev tags.

## Static assets

`@ViteAsset(path)@` in the template, `vite.asset(path)` in code (the `Vite` Spring bean, or `inertia.vite` in Ktor
routes), resolve the URL of a file processed by Vite, given relative to the Vite root:

```html
<link rel="icon" href="@ViteAsset(src/main/frontend/images/favicon.svg)@">
```

In production the file must be listed in the manifest: import it from the frontend, or add it to
`build.rollupOptions.input`, e.g. with `import.meta.glob('./images/**')` in the entry. An unknown file throws
`Unable to locate '...' in the Vite manifest`.

## Content Security Policy nonce

Every tag rendered by `@Vite(...)@` and `@ViteReactRefresh@` (scripts, stylesheets, module preloads, React preamble)
gets a `nonce` attribute when the current request has one. The nonce is looked up on every render, so it can differ
per request.

- **Spring Boot:** set the nonce as a request attribute named `cspNonce` (`inertia.vite.nonce-attribute`), for example
  from the filter writing the `Content-Security-Policy` header:

  ```java
  request.setAttribute("cspNonce", nonce);
  response.setHeader("Content-Security-Policy", "script-src 'nonce-" + nonce + "'");
  ```

- **Ktor:** resolve the nonce of a call, e.g. from a call attribute set by an interceptor:

  ```kotlin
  vite {
      nonce = { call -> call.attributes.getOrNull(CspNonceKey) }
  }
  ```

- **Core:** `ViteConfig.builder().nonceProvider(supplier)`; the supplier is called on the rendering thread.

`vite.cspNonce()` returns the nonce of the current render, e.g. to add it to your own inline scripts.

## Subresource Integrity

When manifest chunks have an `integrity` field, as written by
[vite-plugin-manifest-sri](https://github.com/ElMassimo/vite-plugin-manifest-sri), production script, stylesheet and
`modulepreload` tags get `integrity="..."` and `crossorigin="anonymous"`:

```ts
import manifestSRI from 'vite-plugin-manifest-sri'

plugins: [react(), manifestSRI(), inertia4jHotFile()]
```

The field name is configurable (`inertia.vite.integrity-key` in Spring, `integrityKey` in Ktor,
`ViteConfig.Builder#integrityKey` in core); set it to `false` in Spring, or `null` in Ktor and core, to disable integrity
attributes.

## Configuration

### Spring Boot

| Property | Default | Description |
|---|---|---|
| `inertia.vite.enabled` | `true` | Enables placeholders, asset version and asset serving |
| `inertia.vite.hot-file` | `vite.hot` | File enabling dev mode |
| `inertia.vite.build-directory` | `static/build` | Classpath directory of the build output |
| `inertia.vite.manifest` | `{build-directory}/.vite/manifest.json` | Classpath location of the manifest |
| `inertia.vite.public-path` | `/build/` | URL prefix of built files; a path such as `/build/`, not an absolute URL |
| `inertia.vite.cache-max-age` | `365d` | `max-age` of built files |
| `inertia.vite.integrity-key` | `integrity` | Manifest field holding the SRI hash of chunks; empty or `false` disables it |
| `inertia.vite.nonce-attribute` | `cspNonce` | Request attribute holding the CSP nonce of rendered tags; empty disables it |

Defining your own `Vite`, `VersionProvider` or `TemplateRenderer` bean replaces the default one.

### Ktor

```kotlin
install(Inertia) {
    vite {
        hotFile = Path.of("vite.hot")
        buildDirectory = "static/build"
        manifestPath = null // "$buildDirectory/.vite/manifest.json"
        publicPath = "/build/"
        serveAssets = true
        cacheMaxAge = 365.days
        integrityKey = "integrity" // null disables integrity attributes
        nonce = null // (ApplicationCall) -> String?
    }
}
```

Setting `versionProvider` replaces the Vite asset version.

## Server-side rendering

Enable SSR in Inertia4J (`inertia.ssr.enabled=true` in Spring, `ssr { enabled = true }` in Ktor), then add the
official `@inertiajs/vite` plugin with your SSR entry next to `inertia4jHotFile`:

```ts
import inertia from '@inertiajs/vite'

export default defineConfig(({ command }) => ({
  plugins: [
    react(),
    inertia({ ssr: { entry: 'src/main/frontend/ssr.tsx' } }),
    inertia4jHotFile(),
  ],
  // ...
}))
```

The entry calls `createServer` from your framework adapter (`@inertiajs/react/server`, `@inertiajs/vue3/server`, ...),
as described in the [official guide](https://inertiajs.com/docs/v3/advanced/server-side-rendering).

- **Development:** while `vite.hot` exists, pages are rendered by the dev server through its `/__inertia_ssr` endpoint;
  no SSR build nor separate Node.js process is needed. The endpoint only exists when the plugin finds the SSR entry,
  otherwise every render falls back to client-side rendering and reports a failure. While the dev server warms up it
  answers without markup, and pages are rendered on the client without reporting a failure.
- **Production:** build the SSR bundle with `vite build --ssr` after `vite build`, and run it with `node` next to your
  application, or let the application run it (`inertia.ssr.process.enabled=true` in Spring,
  `ssr { process { enabled = true } }` in Ktor), like `php artisan inertia:start-ssr`. Inertia4J posts pages to
  `http://127.0.0.1:13714/render` by default (`inertia.ssr.url` / `url`).
- **Missing bundle:** point `inertia.ssr.bundle` (Spring) or `bundle` (Ktor) at the file written by `vite build --ssr`.
  While it is missing, pages are rendered client-side without contacting the SSR server nor reporting a failure, so
  an application started without the SSR build keeps working; the check is disabled with
  `inertia.ssr.ensure-bundle-exists=false` / `ensureBundleExists = false`, and skipped while `vite.hot` exists.
- **Health:** `inertia.ssr.check-on-startup=true` / `checkOnStartup = true` logs a warning when the SSR server is
  unreachable on startup, and Spring Boot Actuator reports it as the `inertiaSsr` health indicator.

Setting `inertia.ssr.hot-url` (Spring) or `hotUrl` (Ktor) replaces the URL read from `vite.hot`; it only applies
while the hot file exists.

## Custom template renderers

See [Extending Inertia4J](advanced.md#vite-tags-in-a-custom-renderer).

## Troubleshooting

- **`Vite manifest not found at classpath:...`** — the dev server is not running (no `vite.hot` in the working
  directory) and the frontend was not built. Start `vite`, or run `vite build`.
- **`Unable to locate '...' in the Vite manifest`** — the placeholder entry must match `build.rollupOptions.input`,
  relative to the Vite root; a `@ViteAsset(...)@` file must be imported by the frontend or be an input.
- **Scripts blocked by CORS in development** — set `server.cors.origin` to the backend origin.
- **SSR renders time out or fail with `ConnectException` in development** — with Node.js 17+ (notably Node 24 on
  macOS), Vite binds `localhost` to the IPv6 address `[::1]` only and writes `http://localhost:5173` to `vite.hot`,
  while Java resolves `localhost` to `127.0.0.1`, so the backend cannot reach `/__inertia_ssr`. Set
  `server.host: '127.0.0.1'` and `server.origin: 'http://127.0.0.1:5173'` as in the configuration above, or point
  `inertia.ssr.hot-url` / `hotUrl` at an address the backend can reach.

## Upgrading

Behavior changes for existing users:

- The Vite integration is enabled by default. Without `vite.hot` and without a manifest, the asset version stays `1`
  and templates without placeholders render as before.
- `/build/**` is now served from `classpath:/static/build/` with `Cache-Control: public, max-age=31536000, immutable`.
  Opt out with `inertia.vite.enabled=false` (Spring) or `vite { serveAssets = false }` (Ktor).
- Spring: `inertia.*` properties (`inertia.template-path`, `inertia.encrypt-history`) are now actually bound; they were
  previously ignored.
- Spring: `AbstractInertiaSpringAutoconfiguration#versionProvider` and `#templateRenderer` now take a `Vite`
  parameter. This affects only subclasses of the auto-configuration.
- Ktor: `versionProvider` is now nullable (`(() -> String)?`, default `null` uses the Vite version); setting it is
  unchanged.
- Ktor: the plugin installs routing when `serveAssets` is true. Install `Routing` with `routing { }` rather than
  `install(Routing)` after `install(Inertia)`, or disable `serveAssets`.
