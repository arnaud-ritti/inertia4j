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

## Production

```bash
../../gradlew bootJar
java -jar build/libs/spring-boot-react-example.jar
```

`bootJar` runs `npm ci` and `npm run build`, which writes the bundle and `.vite/manifest.json` to
`src/main/resources/static/build`, packaged in the jar and served under `/build/`.

See [the Vite integration guide](../../docs/vite.md).
