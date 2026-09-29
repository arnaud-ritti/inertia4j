# Roadmap

## 1.0

- **Core**
    - [x] Support asset versioning
    - [x] Make the JSON serializer customizable
    - [x] Support the `encryptHistory` and `clearHistory` flags on response
    - [x] Fill the `url` response element
    - [x] Support partial reloads (via `X-Inertia-Partial-Data` and `X-Inertia-Partial-Component`)
    - [x] Add basic documentation to classes
    - [x] Support Inertia redirects
    - [x] Support non-Inertia redirects
- **Spring**
    - [x] Support asset versioning
    - [x] Setup Vite and [integrate it](https://vite.dev/guide/backend-integration.html) with Spring
    - [x] Make the JSON serializer customizable
    - [x] Support the `encryptHistory` and `clearHistory` flags on response
    - [x] Fill the `url` response element
    - [x] Support partial reloads (via `X-Inertia-Partial-Data` and `X-Inertia-Partial-Component`)
    - [x] Add basic documentation to classes
    - [x] Support Inertia redirects
    - [x] Support non-Inertia redirects
    - [x] Write "getting started" documentation
    - [x] [Autoconfigure](https://www.baeldung.com/spring-boot-custom-auto-configuration) the beans
- **Ktor**
    - [x] Support asset versioning
    - [x] Make the JSON serializer customizable
    - [x] Support the `encryptHistory` and `clearHistory` flags on response
    - [x] Fill the `url` response element
    - [x] Support partial reloads (via `X-Inertia-Partial-Data` and `X-Inertia-Partial-Component`)
    - [x] Add basic documentation to classes
    - [x] Support Inertia redirects
    - [x] Support non-Inertia redirects
    - [x] Write "getting started" documentation
    - [ ] Add testing to example application
    - [x] Setup Vite and [integrate it](https://vite.dev/guide/backend-integration.html) with Ktor

## 1.1

- [x] Shared data
- [x] Deferred props
- [x] Merging props (shallow and deep)
- [x] Vite integration: dev server and manifest tags, asset versioning, asset serving ([guide](vite.md))
- [ ] Generate TypeScript types for props ([inspiration](https://www.youtube.com/watch?v=LeYF1NE3jQ4))
