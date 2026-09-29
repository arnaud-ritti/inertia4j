# Vite Integration — Design

Date: 2026-09-29
Status: Draft, awaiting review
Reference: https://vite.dev/guide/backend-integration.html

## Goal

Make Vite the zero-friction frontend toolchain for Inertia4J apps, following Vite's backend integration guide:

- in development, the root template loads scripts from the Vite dev server, with HMR and React Fast Refresh;
- in production, the root template loads the hashed files listed in `.vite/manifest.json`, with their CSS and
  `modulepreload` hints;
- the Inertia asset version changes whenever the build changes, so clients reload on deploy;
- built assets are served by the application with long-lived cache headers;
- a runnable example app demonstrates the setup.

Success: a Spring Boot or Ktor app adds `@Vite(src/main/frontend/main.tsx)@` to its template, pastes the documented
`vite.config.ts`, and gets correct tags in both modes, automatic versioning and asset serving with no further code.

Today none of this exists: users hardcode `<script>` tags in the template and `VersionProvider` defaults to `"1"`.

## Decisions

| Topic | Decision |
|---|---|
| Location | Inside `inertia4j.core`, package `io.github.inertia4j.core.vite`; no new module |
| Manifest parsing | Small internal JSON reader; core stays free of runtime dependencies (Jackson remains `compileOnly`) |
| Dev mode detection | A "hot" file containing the dev server URL, written by a documented inline Vite plugin; no npm package |
| Production assets | Built into `src/main/resources/static/build`, read from the classpath, served automatically |
| Template API | Placeholders `@Vite(entries)@` and `@ViteReactRefresh@` in `SimpleTemplateRenderer`; public `Vite` API for custom renderers |
| Asset version | SHA-256 of the manifest in production, `"dev"` in development, `"1"` when neither exists |
| Example | `examples/spring-boot-react`, a standalone Gradle build outside the main build and test task |

## Core: `io.github.inertia4j.core.vite`

All classes target Java 11 and are `@NullMarked`.

### `ViteConfig`

Immutable, built with `ViteConfig.builder()`. Defaults are chosen so the documented `vite.config.ts` needs no
matching backend configuration.

| Property | Type | Default | Meaning |
|---|---|---|---|
| `hotFile` | `Path` | `vite.hot` (relative to the working directory) | File whose presence enables dev mode |
| `buildDirectory` | `String` | `static/build` | Classpath directory containing the build output |
| `manifestPath` | `String` | `{buildDirectory}/.vite/manifest.json` | Classpath location of the manifest |
| `publicPath` | `String` | `/build/` | URL prefix of built files; normalised to start and end with `/` |

### `ManifestChunk` and `ViteManifest`

`ManifestChunk` mirrors Vite's `ManifestChunk` interface: `file`, `src`, `name`, `isEntry`, `isDynamicEntry`,
`imports`, `dynamicImports`, `css`, `assets`. Absent fields become `null` (strings), `false` (booleans) or empty
lists.

`ViteManifest` wraps `Map<String, ManifestChunk>` keyed by manifest key, exposing
`Optional<ManifestChunk> chunk(String key)` and `Set<String> entries()` (keys whose chunk has `isEntry`).
`ViteManifest.parse(String json)` builds it through `ManifestJsonReader`.

### `ManifestJsonReader` (package-private)

Recursive-descent parser for RFC 8259 JSON producing `Map`, `List`, `String`, `Boolean`, `Double` and `null`.
Supports string escapes including `\uXXXX`. Errors throw `ViteException` with the character offset.

### `Vite`

Public facade, thread-safe, one instance per application.

- `Vite(ViteConfig config)`.
- `boolean isDevMode()` — `true` when the hot file exists and is readable. Checked on every call; the file content
  is cached keyed by its last-modified time.
- `String devServerUrl()` — trimmed hot file content without trailing `/`. Throws `ViteException` outside dev mode.
- `String tags(String... entries)` — HTML for the given entries (manifest keys, i.e. paths relative to the Vite
  root such as `src/main/frontend/main.tsx`):
  - Dev mode: `<script type="module" src="{url}/@vite/client"></script>` once, then per entry
    `<script type="module" src="{url}/{entry}"></script>`, or `<link rel="stylesheet" href="{url}/{entry}">` when
    the entry is a stylesheet (`.css`, `.scss`, `.sass`, `.less`, `.styl`, `.stylus`, `.pcss`, `.postcss`).
  - Production, following Vite's algorithm across all entries in order:
    1. `<link rel="stylesheet">` for each entry's `css`, then for the `css` of its static `imports`, recursively;
    2. the entry itself: `<script type="module" src>` for a JavaScript chunk, `<link rel="stylesheet">` for a CSS
       entry;
    3. `<link rel="modulepreload">` for each statically imported chunk, recursively.
    Each URL is `publicPath + file` and is emitted at most once for the whole call, even when entries share chunks.
    Tags are grouped per kind: all stylesheets, then all entry scripts, then all preloads.
  - Attribute values are HTML-escaped. Tags are separated by `\n`.
- `String reactRefreshTag()` — in dev mode the preamble from the Vite guide with `{url}/@react-refresh`; in
  production an empty string.
- `String version()` — production: lowercase hex SHA-256 of the manifest bytes; dev: `"dev"`; neither hot file
  nor manifest present: `"1"`.

The manifest is loaded lazily on first production use and cached, together with its hash, for the lifetime of the
instance. Nothing is read at construction, so an application starts even when the frontend is not built.

### `ViteVersionProvider`

`Supplier<String>` delegating to `Vite#version()`, usable directly as the core `InertiaRenderer` version supplier.

### `ViteException`

Unchecked, extends `RuntimeException`.

### `SimpleTemplateRenderer` changes

- New constructor `SimpleTemplateRenderer(String templatePath, @Nullable Vite vite)`; the existing constructor
  delegates with `null`.
- When `vite` is non-null, `render` first replaces in the template:
  - `@ViteReactRefresh@` with `vite.reactRefreshTag()`;
  - every `@Vite(a, b)@` (pattern `@Vite\(([^)]*)\)@`, entries split on `,` and trimmed, empty entries rejected)
    with `vite.tags(a, b)`;
  and then injects the page object as today. Substituting Vite placeholders first guarantees that page data
  containing `@Vite(` is never interpreted.
- When `vite` is `null`, placeholders are left untouched, so existing templates and behaviour are unchanged.

Custom `TemplateRenderer` implementations (Thymeleaf, Freemarker, …) obtain the `Vite` instance and call
`tags(...)` / `reactRefreshTag()` directly.

## Dev server hot file

No npm package is published. `docs/vite.md` documents this `vite.config.ts`:

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

export default defineConfig({
  plugins: [react(), inertia4jHotFile()],
  base: '/build/',
  build: {
    manifest: true,
    outDir: 'src/main/resources/static/build',
    emptyOutDir: true,
    rollupOptions: { input: 'src/main/frontend/main.tsx' },
  },
  server: {
    origin: 'http://localhost:5173',
    cors: { origin: 'http://localhost:8080' },
  },
})
```

The entry module starts with `import 'vite/modulepreload-polyfill'`. `vite.hot` and the build directory are added to
`.gitignore`. The backend must run with the Vite project root as working directory, or configure `hotFile`
explicitly.

## Spring (`inertia4j.spring-shared`, used by Boot 3 and Boot 4)

### Properties

`InertiaConfigurationProperties` gains a nested `vite` group:

| Property | Default |
|---|---|
| `inertia.vite.enabled` | `true` |
| `inertia.vite.hot-file` | `vite.hot` |
| `inertia.vite.build-directory` | `static/build` |
| `inertia.vite.manifest` | `{build-directory}/.vite/manifest.json` |
| `inertia.vite.public-path` | `/build/` |
| `inertia.vite.cache-max-age` | `365d` (`Duration`) |

### Beans in `AbstractInertiaSpringAutoconfiguration`

- `@ConditionalOnMissingBean Vite vite(InertiaConfigurationProperties)`.
- The default `VersionProvider` returns `vite.version()` when Vite is enabled, `"1"` otherwise.
- The default `TemplateRenderer` becomes `new SimpleTemplateRenderer(templatePath, enabled ? vite : null)`.
- `WebMvcConfigurer`, conditional on `inertia.vite.enabled=true` and on a servlet web application, registering
  `{publicPath}**` → `classpath:/{buildDirectory}/` with `CacheControl.maxAge(cacheMaxAge).cachePublic().immutable()`.
  The convention plugin `inertia4j.spring-conventions` adds `compileOnly("org.springframework:spring-webmvc")`, since
  only `spring-web` is available today.

Users who define their own `VersionProvider`, `TemplateRenderer` or `Vite` bean keep full control.

## Ktor (`inertia4j.ktor`)

`InertiaKtorConfiguration` gains:

```kotlin
install(Inertia) {
    vite {
        hotFile = Path.of("vite.hot")
        buildDirectory = "static/build"
        manifestPath = "static/build/.vite/manifest.json" // defaults to "$buildDirectory/.vite/manifest.json"
        publicPath = "/build/"
        serveAssets = true
        cacheMaxAge = 365.days
    }
}
```

- The default `versionProvider` becomes the Vite version; an explicitly set provider still wins.
- The default template renderer receives the `Vite` instance.
- When `serveAssets` is true, the plugin registers `staticResources(publicPath, buildDirectory)` inside
  `application.routing { }` with a `Cache-Control: public, max-age=…, immutable` header.

## Example app: `examples/spring-boot-react`

- Standalone Gradle build with its own `settings.gradle.kts` using `includeBuild("../..")`, so it consumes the local
  `inertia4j-spring-boot-3` through dependency substitution; not part of the root build or `./gradlew test`.
- Spring Boot 3 application with two controllers rendering `Home` and `About` pages with props and navigation.
- Frontend in `src/main/frontend`: Vite, React, TypeScript, `@inertiajs/react`, the `vite.config.ts` above.
- Template `src/main/resources/templates/app.html` uses `@ViteReactRefresh@` and
  `@Vite(src/main/frontend/main.tsx)@`.
- Gradle `Exec` tasks `npmInstall` (`npm ci`) and `npmBuild` (`npm run build`); `processResources` depends on
  `npmBuild`.
- README with the dev workflow (`npm run dev` + `./gradlew bootRun`) and the production workflow (`./gradlew
  bootJar`).
- CI: new job `example` in `.github/workflows/ci.yml` with `actions/setup-node` and
  `./gradlew -p examples/spring-boot-react build`.

## Error handling

| Situation | Behaviour |
|---|---|
| Production, manifest missing | `ViteException`: "Vite manifest not found at classpath:{path}. Start the Vite dev server or run the frontend build." Raised when tags are rendered, never at startup |
| Unknown entry | `ViteException` naming the entry and listing `entries()` |
| Malformed manifest | `ViteException` with the offset reported by the reader |
| Hot file exists but unreadable | Treated as production; warning logged through `System.Logger` |
| Empty `@Vite()@` placeholder or empty entry | `ViteException` identifying the placeholder |

`version()` never throws: without a readable manifest it falls back to `"1"`, so Inertia protocol handling keeps
working while tag rendering reports the actionable error.

## Testing

TDD throughout.

- `inertia4j.core`:
  - `ManifestJsonReaderTest` — objects, arrays, escapes, unicode, literals, numbers, malformed input offsets.
  - `ViteTest` with a fixture manifest based on the Vite guide example (`views/foo.js`, `views/bar.js`,
    `_shared-*.js` with CSS, `baz.js` dynamic import, a CSS entry):
    tag order for one entry, recursive CSS, deduplication across two entries, CSS entry, dynamic imports ignored,
    unknown entry, missing manifest, dev mode tags via a temporary hot file, trailing slash in the hot file,
    React preamble in dev and production, version in all three states and stable across calls, HTML escaping.
  - `SimpleTemplateRendererTest` — placeholders replaced, multiple entries, multiple placeholders, no `Vite`
    leaves the template unchanged, page JSON containing `@Vite(x)@` is not substituted.
- `inertia4j.spring-boot-3` and `inertia4j.spring-boot-4` MockMvc tests with a fixture
  `src/test/resources/static/build/.vite/manifest.json` and asset: the HTML contains the expected tags, a request
  with a stale `X-Inertia-Version` gets 409 while one with the manifest hash succeeds, `GET /build/assets/…` is
  served with the immutable `Cache-Control` header.
- `inertia4j.ktor` — the same three scenarios with `testApplication`.

## Documentation

- New `docs/vite.md`: setup, `vite.config.ts`, template placeholders, dev and production workflows, configuration
  reference for Spring and Ktor, notes for Vue and Svelte (omit `@ViteReactRefresh@`).
- Spring Boot 3, Spring Boot 4 and Ktor READMEs: template example uses the placeholders; link to `docs/vite.md`.
- `docs/advanced.md`: using `Vite` from a custom `TemplateRenderer`.
- `docs/roadmap.md`: mark the Vite items accurately.

## Out of scope

- Publishing an npm Vite plugin.
- Server-side rendering (SSR) of Vite bundles.
- `build.chunkImportMap` / import maps.
- Proxying the Vite dev server through the backend.
- Integrity (SRI) attributes and CSP nonces on generated tags.
- Ktor example app.
