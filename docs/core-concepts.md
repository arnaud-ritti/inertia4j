# Core concepts

Inertia sits between a classic server-rendered application and a single-page application. Routing, controllers,
authorization and validation stay on the server; views are components of a frontend framework. This page explains
what happens on each request and names the pieces the rest of the documentation refers to.

## Visits

**First visit.** The browser requests a URL normally. The controller calls `render(component, props)` and Inertia4J
answers with the [root template](guides/responses.md#the-root-template): a full HTML document embedding the page
object. The client boots, reads the page object and mounts the component.

**Inertia visit.** Links and forms handled by the client (`<Link>`, `router.visit`, `useForm`) send an XHR request
with the `X-Inertia: true` header. The same controller runs, and Inertia4J answers with the page object as JSON. The
client swaps the component and updates the URL without reloading the document.

Your controllers don't need to know which kind of visit they serve: `render` picks the response format.

## The page object

Every response carries a page object:

```json
{
  "component": "Users/Index",
  "props": { "users": [], "errors": {} },
  "url": "/users?page=2",
  "version": "6f1c2e…",
  "deferredProps": { "default": ["permissions"] }
}
```

- `component` is the name your client resolves to a page component.
- `props` are the data passed to it, serialized to JSON (by Jackson unless you
  [replace the serializer](guides/extending.md#serialization)).
- `url` is the URL of the page, used by the client for history entries.
- `version` is the [asset version](guides/asset-versioning.md).
- Metadata fields (`deferredProps`, `mergeProps`, `onceProps`, `encryptHistory`, …) are only present when used.

See the [protocol](https://inertiajs.com/docs/v3/core-concepts/the-protocol) for the full specification.

## Props are resolved lazily

Props can be plain values, `Supplier`s (Java) or functions (Kotlin), or `InertiaProp` wrappers created with
`Inertia.defer(...)`, `InertiaProps.merge(...)` and friends. Inertia4J resolves a prop only when it is part of the
response, which is what makes [partial reloads](guides/props.md#partial-reloads) and
[deferred props](guides/props.md#deferred-props) cheap: a prop that isn't requested is never computed.

## Shared props

Some data belongs to every page: the signed-in user, flash messages, the application name. Instead of adding it in
each controller, register it once as [shared data](guides/shared-data.md). Shared props are merged with the page's own
props, which win on key collisions.

## Session-backed data

Flash messages, validation errors and a few redirect flags must survive a redirect. Inertia4J keeps them in the HTTP
session (Spring) or in the Ktor `Sessions` plugin until the next page is rendered. See
[Forms and validation](guides/forms-and-validation.md).

## The protocol middleware

Some protocol rules apply to responses that don't go through `render`: a `302` answering a `PUT` request must become a
`303`, an outdated client must receive `409 Conflict`, and every response needs `Vary: X-Inertia`. Inertia4J enforces
them with a servlet filter in Spring (`InertiaFilter`) and inside the Ktor plugin. See
[Redirects](guides/redirects.md#redirects-that-bypass-inertia).

## Where to go from here

- The [architecture overview](architecture.md) maps these concepts to modules and classes.
- The [configuration reference](reference/configuration.md) lists every setting.
