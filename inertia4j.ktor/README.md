# Inertia4J for Ktor

Inertia.js adapter for Ktor 3 (Java 11+), provided as an application plugin.

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.inertia4j:inertia4j-ktor:2.0.0")
    implementation("io.ktor:ktor-server-sessions:3.0.0")                  // flash data and validation errors
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2") // default JSON serializer
}
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.github.inertia4j</groupId>
    <artifactId>inertia4j-ktor</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Usage

```kotlin
import io.github.inertia4j.core.InertiaProps
import io.github.inertia4j.ktor.Inertia
import io.github.inertia4j.ktor.InertiaSession
import io.github.inertia4j.ktor.inertia

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
- [Vite integration](../docs/vite.md) and [TypeScript types](../docs/typescript.md)
- [Configuration reference](../docs/reference/configuration.md#ktor-plugin-settings)
