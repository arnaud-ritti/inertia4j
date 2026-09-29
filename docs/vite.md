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
    port: 5173,
    strictPort: true,
    origin: 'http://localhost:5173',
    cors: { origin: 'http://localhost:8080' },
  },
}))
```

- `inertia4jHotFile` writes the dev server URL to `vite.hot` while `vite` runs; Inertia4J switches to dev mode
  when the file exists.
- `base` applies to the build only, matching the URL prefix under which Inertia4J serves built files.
- `server.cors.origin` must be the origin of your backend.
- `strictPort` makes Vite fail instead of moving to another port, since `server.origin` must match the dev server URL; change `server.port` and `server.origin` together.

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
</head>
<body>
  <div id="app" data-page='@PageObject@'></div>
</body>
```

- `@Vite(a, b)@` renders the tags of one or more entries, given as paths relative to the Vite root.
- `@ViteReactRefresh@` renders the React Fast Refresh preamble in dev mode; omit it for Vue, Svelte and other
  frameworks.

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

## Configuration

### Spring Boot

| Property | Default | Description |
|---|---|---|
| `inertia.vite.enabled` | `true` | Enables placeholders, asset version and asset serving |
| `inertia.vite.hot-file` | `vite.hot` | File enabling dev mode |
| `inertia.vite.build-directory` | `static/build` | Classpath directory of the build output |
| `inertia.vite.manifest` | `{build-directory}/.vite/manifest.json` | Classpath location of the manifest |
| `inertia.vite.public-path` | `/build/` | URL prefix of built files |
| `inertia.vite.cache-max-age` | `365d` | `max-age` of built files |

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
    }
}
```

Setting `versionProvider` replaces the Vite asset version.

## Custom template renderers

See [Advanced usage](advanced.md#vite-tags-in-a-custom-renderer).

## Troubleshooting

- **`Vite manifest not found at classpath:...`** — the dev server is not running (no `vite.hot` in the working
  directory) and the frontend was not built. Start `vite`, or run `vite build`.
- **`Unable to locate '...' in the Vite manifest`** — the placeholder entry must match `build.rollupOptions.input`,
  relative to the Vite root.
- **Scripts blocked by CORS in development** — set `server.cors.origin` to the backend origin.
