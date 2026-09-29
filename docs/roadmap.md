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
    - [x] Setup Vite and [integrate it](https://v3.vitejs.dev/guide/backend-integration.html) with Spring for development
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
    - [ ] Setup Vite and [integrate it](https://v3.vitejs.dev/guide/backend-integration.html) with Ktor for development

## 1.1

- [x] Shared data
- [x] Deferred props
- [x] Merging props (shallow and deep)
- [ ] Generate TypeScript types for props ([inspiration](https://www.youtube.com/watch?v=LeYF1NE3jQ4))

## 2.0 — Inertia.js v3 protocol

- [x] Initial page object in a `<script type="application/json">` element, with `@InertiaHead@` / `@InertiaApp@` placeholders
- [x] `Vary: X-Inertia`, response status, page URL with query string
- [x] Asset version mismatch per the v3 protocol (absolute location, `X-Inertia-Version` echo, flash kept)
- [x] Partial reloads: component check, `only`/`except` together, dotted paths
- [x] Optional, always, once, infinite scroll and rescued deferred props
- [x] `sharedProps`, `flash`, `preserveFragment` page object fields
- [x] Validation errors and error bags
- [x] Fragment redirects (`X-Inertia-Redirect`)
- [x] Precognition
- [x] Server-side rendering
