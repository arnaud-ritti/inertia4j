# Inertia4J — Spring Boot + React example

A Spring Boot 3 application rendering React pages through Inertia4J, bundled by Vite.
It builds against the Inertia4J modules of this repository.

## Development

Run the Vite dev server and the application in two terminals, from this directory:

```bash
npm install
npm run dev
```

```bash
../../gradlew bootRun
```

Open http://localhost:8080. While `npm run dev` runs, it writes `vite.hot`, so pages load scripts from the dev server
with hot module replacement. Stop it and the application falls back to the production build.
The dev server must run on port 5173 (`strictPort`); if that port is taken, change `server.port` and `server.origin` together in `vite.config.ts`.
The dev server listens on `127.0.0.1` so that the URL written to `vite.hot` is reachable from the JVM.

## Production

```bash
../../gradlew bootJar
java -jar build/libs/spring-boot-react-example.jar
```

`bootJar` runs `npm ci` and `npm run build`, which writes the bundle and `.vite/manifest.json` to
`src/main/resources/static/build`, packaged in the jar and served under `/build/`.

## Server-side rendering

Pages are rendered on the server (`inertia.ssr.enabled=true` in `application.properties`) through the official
`@inertiajs/vite` plugin, with the entry `src/main/frontend/ssr.tsx`.

- **Development:** nothing more to run. While `npm run dev` runs, the application renders pages through the dev
  server's `/__inertia_ssr` endpoint.
- **Production:** `npm run build` also builds the SSR bundle to `build/ssr/ssr.js` (`vite build --ssr`). Run it next
  to the application; it listens on `http://127.0.0.1:13714`, where Inertia4J posts pages by default:

  ```bash
  npm run ssr
  ```

When the SSR server is not reachable, pages fall back to client-side rendering, and the client entry mounts the page
instead of hydrating it.

See [the Vite integration guide](../../docs/guides/vite.md).
