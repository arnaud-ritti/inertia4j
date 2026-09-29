# Inertia4J for Ktor

Inertia.js adapter for Ktor 3 (Java 11+), provided as an application plugin.

## Installation

Artifacts are published to GitHub Packages; add the repository first, see
[Installation](../docs/installation.md).

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.arkoder:inertia4j-ktor:3.0.0")
    implementation("io.ktor:ktor-server-sessions:3.0.0")                  // flash data and validation errors
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2") // default JSON serializer
}
```

```xml
<!-- pom.xml: Ktor artifacts need the -jvm suffix with Maven -->
<dependency>
    <groupId>dev.arkoder</groupId>
    <artifactId>inertia4j-ktor</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>io.ktor</groupId>
    <artifactId>ktor-server-sessions-jvm</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.2</version>
</dependency>
```

See the [Maven guide](../docs/guides/maven.md) for the frontend build and TypeScript types.

## Usage

```kotlin
import dev.arkoder.inertia4j.core.InertiaProps
import dev.arkoder.inertia4j.ktor.Inertia
import dev.arkoder.inertia4j.ktor.InertiaSession
import dev.arkoder.inertia4j.ktor.inertia

fun Application.module() {
    install(Sessions) {
        cookie<InertiaSession>("INERTIA_SESSION", SessionStorageMemory()) {
            serializer = InertiaSession.Serializer
        }
    }

    install(Inertia) {
        share { call -> mapOf("user" to { currentUser(call) }) }
    }

    routing {
        get("/users") {
            inertia.render(
                "Users/Index",
                "users" to userRepository.findAll(),
                "permissions" to InertiaProps.defer { permissionRepository.findAll() },
            )
        }

        post("/users") {
            val form = call.receive<UserForm>()
            val errors = validate(form)

            if (errors.isNotEmpty()) {
                inertia.errors(errors)
                return@post inertia.back()
            }

            userRepository.create(form)
            inertia.flash("message", "User created")
            inertia.redirect("/users")
        }
    }
}
```

The plugin serves the Vite build under `/build/` and installs routing for it. Use `routing { }` rather than
`install(Routing)` after `install(Inertia)`, or disable it with `vite { serveAssets = false }`.

## Documentation

- [Getting started](../docs/getting-started.md)
- Guides: [responses and templates](../docs/guides/responses.md), [redirects](../docs/guides/redirects.md),
  [props](../docs/guides/props.md), [shared data](../docs/guides/shared-data.md),
  [forms and validation](../docs/guides/forms-and-validation.md), [asset versioning](../docs/guides/asset-versioning.md),
  [server-side rendering](../docs/guides/ssr.md), [testing](../docs/guides/testing.md)
- [Vite integration](../docs/guides/vite.md), [TypeScript types](../docs/guides/typescript.md) and [Maven builds](../docs/guides/maven.md)
- [Configuration reference](../docs/reference/configuration.md#ktor-plugin-settings)
