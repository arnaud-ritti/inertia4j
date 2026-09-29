<div align="center">

<img src="docs/assets/logo.webp" width="280" alt="Inertia4J"/>

<h2>Inertia4J</h2>

<p>Server-side <a href="https://inertiajs.com/">Inertia.js</a> adapter for Spring Boot and Ktor.</p>

<a href="https://github.com/arnaud-ritti/inertia4j/actions/workflows/ci.yml"><img src="https://github.com/arnaud-ritti/inertia4j/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"/></a>
<a href="LICENSE"><img src="https://img.shields.io/github/license/arnaud-ritti/inertia4j.svg?style=flat" alt="License"/></a>

</div>

Build single-page applications with React, Vue or Svelte while keeping routing, controllers and validation on the
JVM. Controllers return a component name and its props; Inertia takes care of the rest. No REST API to design, no
client-side router to maintain.

```java
@GetMapping("/users")
public ResponseEntity<String> index() {
    return inertia.render("Users/Index", Map.of("users", users.findAll()));
}
```

```tsx
export default function Index({ users }: { users: User[] }) {
  return <ul>{users.map((user) => <li key={user.id}>{user.name}</li>)}</ul>
}
```

## Features

- Full [Inertia.js v3 protocol](https://inertiajs.com/docs/v3/core-concepts/the-protocol): partial reloads,
  deferred, merge, once and infinite scroll props, shared data, flash data, validation errors, error bags,
  Precognition and history encryption.
- Adapters for **Spring Boot 3**, **Spring Boot 4** and **Ktor 3**, with the same API in Java and Kotlin.
- **Vite** integration: dev server with hot reload, production manifest, automatic asset versioning, CSP nonces and
  Subresource Integrity.
- **Server-side rendering** through the Inertia Node.js server, with a managed process, health checks and client-side
  fallback.
- **TypeScript types** generated from your props classes by a Gradle plugin.
- **Test assertions** for MockMvc and Ktor's `testApplication`, modeled on Laravel's `assertInertia`.
- Pluggable JSON serializer, template renderer and SSR gateway.

## Installation

| Framework                 | Artifact                                        | Requires |
|---------------------------|-------------------------------------------------|----------|
| Spring Boot 3 (MVC)       | `io.github.inertia4j:inertia4j-spring-boot-3`   | Java 17  |
| Spring Boot 4 (MVC)       | `io.github.inertia4j:inertia4j-spring-boot-4`   | Java 17  |
| Ktor 3                    | `io.github.inertia4j:inertia4j-ktor`            | Java 11  |

```kotlin
dependencies {
    implementation("io.github.inertia4j:inertia4j-spring-boot-3:2.0.0")
}
```

Inertia4J 2.x works with Inertia.js v3 clients. Coming from 1.x? Read the [migration guide](docs/migration-2.0.md).

Follow [Getting started](docs/getting-started.md) for the frontend setup and your first page.

## Documentation

- [Getting started](docs/getting-started.md) and [core concepts](docs/core-concepts.md)
- Guides: [responses and templates](docs/guides/responses.md), [redirects](docs/guides/redirects.md),
  [props](docs/guides/props.md), [shared data](docs/guides/shared-data.md),
  [forms and validation](docs/guides/forms-and-validation.md), [asset versioning](docs/guides/asset-versioning.md),
  [server-side rendering](docs/guides/ssr.md), [testing](docs/guides/testing.md)
- [Vite integration](docs/vite.md), [TypeScript types](docs/typescript.md),
  [extending Inertia4J](docs/advanced.md)
- [Configuration reference](docs/reference/configuration.md) and [architecture](docs/architecture.md)

Framework pages: [Spring Boot 3](inertia4j.spring-boot-3/README.md), [Spring Boot 4](inertia4j.spring-boot-4/README.md),
[Ktor](inertia4j.ktor/README.md). A runnable [Spring Boot + React example](examples/spring-boot-react) lives in this
repository.

## Contributing

Contributions are welcome. Read the [contributing guide](CONTRIBUTING.md) for the development setup and pull request
process, and the [code of conduct](CODE_OF_CONDUCT.md). Report security issues privately as described in the
[security policy](SECURITY.md).

Inertia4J is maintained by [@arnaud-ritti](https://github.com/arnaud-ritti).

## License

Released under the [Apache License 2.0](LICENSE).
