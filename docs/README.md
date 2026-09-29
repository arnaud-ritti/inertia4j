# Inertia4J documentation

Inertia4J is a server-side adapter for [Inertia.js](https://inertiajs.com/) on the JVM. It lets Spring Boot and Ktor
applications render React, Vue or Svelte pages without building a separate API: controllers return a component name
and its props, and the Inertia client takes care of the rest.

Inertia4J 2.x implements the [Inertia.js v3 protocol](https://inertiajs.com/docs/v3/core-concepts/the-protocol).

## Start here

- [Getting started](getting-started.md): install the adapter, set up the frontend and render your first page.
- [Core concepts](core-concepts.md): how a request flows through Inertia4J, and the vocabulary used in these docs.

## Guides

Each guide shows both the Spring Boot (Java) and the Ktor (Kotlin) API.

| Guide                                                  | Covers                                                                   |
|--------------------------------------------------------|--------------------------------------------------------------------------|
| [Responses and templates](guides/responses.md)         | `render`, typed props, response options, the root HTML template          |
| [Redirects](guides/redirects.md)                       | `redirect`, `location`, `back`, 303 and fragment handling                |
| [Props](guides/props.md)                               | Partial reloads, lazy, optional, always, deferred, merge, once and scroll |
| [Shared data](guides/shared-data.md)                   | Data sent with every page                                                |
| [Forms and validation](guides/forms-and-validation.md) | Flash data, validation errors, error bags, Precognition, CSRF            |
| [Asset versioning](guides/asset-versioning.md)         | Forcing clients to reload after a deployment                             |
| [Server-side rendering](guides/ssr.md)                 | Rendering the first visit with the Node.js SSR server                    |
| [Testing](guides/testing.md)                           | Asserting Inertia responses in MockMvc and Ktor tests                    |
| [Vite integration](guides/vite.md)                            | Dev server, production build, CSP nonces, Subresource Integrity          |
| [TypeScript types](guides/typescript.md)                      | Generating TypeScript declarations from props classes                    |
| [Maven](guides/maven.md)                                      | Dependencies, frontend build and TypeScript types in a Maven project     |
| [Extending Inertia4J](guides/extending.md)                     | Custom serializers, template renderers, SSR gateways, other frameworks   |

## Reference

- [Configuration](reference/configuration.md): every Spring Boot property and Ktor plugin setting, with defaults.
- [Architecture](architecture.md): modules, request lifecycle and extension points, for contributors.
- [Migrating from 1.x to 2.0](migration-2.0.md)

## Examples

- [Spring Boot + React](../examples/spring-boot-react): Vite, server-side rendering, typed props.
