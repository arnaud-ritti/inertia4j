# Getting started

This guide takes you from an empty backend to a first page rendered by React through Inertia4J. The steps are the same
for Vue and Svelte; only the client adapter changes.

## Requirements

| Adapter                             | Runtime                          | Framework          |
|-------------------------------------|----------------------------------|--------------------|
| `inertia4j-spring-boot-3`           | Java 17+                         | Spring Boot 3.x (Spring MVC) |
| `inertia4j-spring-boot-4`           | Java 17+                         | Spring Boot 4.x (Spring MVC) |
| `inertia4j-ktor`                    | Java 11+                         | Ktor 3.x           |

The frontend needs Node.js 20 or newer and an Inertia.js **v3** client adapter (`@inertiajs/react`, `@inertiajs/vue3`
or `@inertiajs/svelte`). Inertia4J 1.x supports older clients; see the [migration guide](migration-3.0.md) to upgrade.

Artifacts are published to GitHub Packages. Add the repository to your build first: see
[Installation](installation.md).

## 1. Add the dependency

### Spring Boot

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.arkoder:inertia4j-spring-boot-3:3.0.0") // or inertia4j-spring-boot-4
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>dev.arkoder</groupId>
    <artifactId>inertia4j-spring-boot-3</artifactId> <!-- or inertia4j-spring-boot-4 -->
    <version>3.0.0</version>
</dependency>
```

The adapter is auto-configured: an `Inertia` bean is ready to inject, with the Inertia filter and the Vite integration
registered.

### Ktor

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.arkoder:inertia4j-ktor:3.0.0")
    implementation("io.ktor:ktor-server-netty:3.0.0")
    implementation("io.ktor:ktor-server-sessions:3.0.0")          // flash data and validation errors
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2") // default JSON serializer
}
```

Install the plugin in your application module:

```kotlin
import dev.arkoder.inertia4j.ktor.Inertia

fun Application.module() {
    install(Inertia)
}
```

Jackson is only needed by the default page object serializer; you can [plug in your own](guides/extending.md#serialization).

Using Maven? The [Maven guide](guides/maven.md#dependencies) lists the equivalent `pom.xml` entries; Ktor artifacts need
their `-jvm` suffix there.

## 2. Create the root template

The first visit to your application returns a full HTML document; later visits only exchange JSON. Create
`src/main/resources/templates/app.html`:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    @ViteReactRefresh@
    @Vite(src/main/frontend/main.tsx)@
    @InertiaHead@<title>My app</title>@EndInertiaHead@
  </head>
  <body>
    @InertiaApp@
  </body>
</html>
```

`@InertiaApp@` becomes the page data and the element the client mounts on. `@Vite(...)@` loads your frontend from the
Vite dev server or from the production build. The other placeholders are described in
[Responses and templates](guides/responses.md#the-root-template).

## 3. Set up the frontend

Install the client dependencies at the root of your project:

```shell
npm install @inertiajs/react react react-dom
npm install --save-dev vite @vitejs/plugin-react typescript @types/react @types/react-dom
```

Add a `vite.config.ts` that writes the `vite.hot` file and builds into `src/main/resources/static/build`. The complete
file, with an explanation of each option, is in the [Vite integration guide](guides/vite.md#1-configure-vite).

Create the entry point, `src/main/frontend/main.tsx`:

```tsx
import 'vite/modulepreload-polyfill'
import { createRoot } from 'react-dom/client'
import { createInertiaApp } from '@inertiajs/react'

const pages = import.meta.glob('./pages/**/*.tsx', { eager: true })

createInertiaApp({
  resolve: (name) => pages[`./pages/${name}.tsx`],
  setup({ el, App, props }) {
    createRoot(el).render(<App {...props} />)
  },
})
```

And a first page, `src/main/frontend/pages/Home.tsx`:

```tsx
import { Link } from '@inertiajs/react'

export default function Home({ message }: { message: string }) {
  return (
    <main>
      <h1>{message}</h1>
      <Link href="/about">About</Link>
    </main>
  )
}
```

Add `vite.hot` and `src/main/resources/static/build/` to `.gitignore`.

## 4. Render the page

### Spring Boot

```java
import dev.arkoder.inertia4j.springboot3.Inertia;

@RestController
public class PagesController {
    private final Inertia inertia;

    public PagesController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GetMapping("/")
    public ResponseEntity<String> home() {
        return inertia.render("Home", Map.of("message", "Hello from Spring Boot"));
    }
}
```

### Ktor

```kotlin
import dev.arkoder.inertia4j.ktor.Inertia
import dev.arkoder.inertia4j.ktor.inertia

fun Application.module() {
    install(Inertia)

    routing {
        get("/") {
            inertia.render("Home", "message" to "Hello from Ktor")
        }
    }
}
```

`"Home"` is the component name passed to `resolve` on the client; the props become the component's props.

## 5. Run it

In development, run Vite and the backend side by side:

```shell
npx vite            # terminal 1: writes vite.hot, serves modules with hot reload
./gradlew bootRun   # terminal 2 (Spring Boot), or ./gradlew run for Ktor, or ./mvnw spring-boot:run
```

Open `http://localhost:8080`. Clicking the link performs an Inertia visit: the client asks the server for the next
page as JSON and swaps the component without reloading the document.

For production, run `npx vite build` before packaging. The backend then serves the hashed files from
`/build/`, and the asset version changes with each build so browsers pick up new deployments. The
[example application](../examples/spring-boot-react) wires `npm run build` into Gradle's `processResources`; the
[Maven guide](guides/maven.md#building-the-frontend) does the same with the `exec-maven-plugin`.

## Next steps

- Learn the [core concepts](core-concepts.md) behind visits, page objects and partial reloads.
- Share the current user with every page: [Shared data](guides/shared-data.md).
- Handle a form submission: [Forms and validation](guides/forms-and-validation.md).
- Type your props end to end: [TypeScript types](guides/typescript.md).
