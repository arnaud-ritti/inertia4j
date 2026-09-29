# Migrating from Inertia4J 1.x to 3.0

Inertia4J 3.0 implements the [Inertia.js v3 protocol](https://inertiajs.com/docs/v3/core-concepts/the-protocol).
Upgrade the Inertia client adapter (`@inertiajs/react`, `@inertiajs/vue3`, `@inertiajs/svelte`) to v3 along with it.

## Coordinates and packages

Inertia4J's version now follows the Inertia.js protocol, so the release after 1.x is 3.0; there is no 2.x. Artifacts,
packages and the Gradle plugin moved to a new namespace, and releases are published to
[GitHub Packages](installation.md):

| What              | 1.x                              | 3.0                                   |
|-------------------|----------------------------------|---------------------------------------|
| Maven `groupId`   | `io.github.inertia4j`            | `dev.arkoder`                         |
| Java packages     | `io.github.inertia4j.*`          | `dev.arkoder.inertia4j.*`             |
| Gradle plugin id  | none                             | `dev.arkoder.inertia4j.typescript`    |
| Repository        | Maven Central                    | GitHub Packages                       |

Update your dependencies and replace the package prefix in imports:

```shell
grep -rl 'io.github.inertia4j' src | xargs sed -i '' 's/io\.github\.inertia4j/dev.arkoder.inertia4j/g'
```

(On Linux, use `sed -i` without the empty `''` argument.)

## HTML template

Inertia v3 reads the initial page object from a `<script type="application/json">` element instead of the `data-page`
attribute of the root element. Replace the element holding `@PageObject@` with `@InertiaApp@`, and add `@InertiaHead@`
to the `<head>` for server-side rendering:

```diff
   <head>
     <title>My app</title>
+    @InertiaHead@
   </head>
   <body>
-    <div id="app" data-page='@PageObject@'></div>
+    @InertiaApp@
   </body>
```

Templates still containing `@PageObject@` are rejected at startup.

## Props

`DeferredProp`, `MergeProp` and `MergeableProp` are replaced by a single `InertiaProp<T>`, whose behaviors compose. The
`InertiaProps` factories (and the `Inertia` static methods in Spring) keep their names, so most code compiles
unchanged; update code referencing the removed types:

```diff
- DeferredProp permissions = InertiaProps.defer(() -> fetchPermissions());
+ InertiaProp<List<Permission>> permissions = InertiaProps.defer(() -> fetchPermissions());
```

New prop types: `optional`, `always`, `once`, `scroll`, and `rescue()` for deferred props.

## Responses

- Every page object has an `errors` prop, `{}` by default, and lists shared prop keys in `sharedProps`
  (disable with `inertia.expose-shared-prop-keys=false` in Spring, `exposeSharedPropKeys = false` in Ktor).
- `encryptHistory` and `clearHistory` are only sent when `true`.
- The page `url` includes the query string.
- Responses carry `Vary: X-Inertia`; HTML responses are `text/html; charset=utf-8`.
- An Inertia `GET` request without `X-Inertia-Version` is an asset version mismatch. The `409` response points to the
  absolute request URL and echoes the current version in `X-Inertia-Version`.
- A partial reload targeting another component than the rendered one returns a full response. In 1.x, the response
  took the component name from the request header.
- `location(url)` returns a plain `302` redirect for non-Inertia requests.
- Redirects to a URL with a fragment return `409` with `X-Inertia-Redirect` for Inertia requests.
- The Spring filter and the Ktor plugin apply these rules to every Inertia request, not only to `inertia.*` responses:
  outdated asset versions are answered with `409` before the handler runs, and a `302` answering a `PUT`, `PATCH` or
  `DELETE` request becomes a `303`.
- Server-side render requests time out after 10 seconds by default, and use the Vite dev server while it runs; a
  configured hot URL only applies while it runs. SSR exclusion paths match with or without leading slash, relative to
  the servlet context path.
- Once prop `expiresAt` timestamps keep their milliseconds.

## Vite integration

The [Vite integration](guides/vite.md) is new in 3.0:

- The Vite integration is enabled by default. Without `vite.hot` and without a manifest, the asset version stays `1`
  and templates without placeholders render as before.
- `/build/**` is now served from `classpath:/static/build/` with `Cache-Control: public, max-age=31536000, immutable`.
  Opt out with `inertia.vite.enabled=false` (Spring) or `vite { serveAssets = false }` (Ktor).
- Spring: `inertia.*` properties (`inertia.template-path`, `inertia.encrypt-history`) are now actually bound; they were
  previously ignored.
- Spring: `AbstractInertiaSpringAutoconfiguration#versionProvider` and `#templateRenderer` now take a `Vite`
  parameter. This affects only subclasses of the auto-configuration.
- Ktor: `versionProvider` is now nullable (`(() -> String)?`, default `null` uses the Vite version); setting it is
  unchanged.
- Ktor: the plugin installs routing when `serveAssets` is true. Install `Routing` with `routing { }` rather than
  `install(Routing)` after `install(Inertia)`, or disable `serveAssets`.

## Extension points

- `PageObjectSerializer.serialize(PageObject)` no longer receives the partial reload props: props are filtered before
  serialization. Serializers must omit empty or `false` metadata fields.
- `TemplateRenderer.render(RenderedPage)` receives the head and body markup instead of the page object JSON.
- `PageObject` is created with `PageObject.builder(component, url, version)`.
- `HttpRequest` implementations must provide `getUrl()` (path and query string) and `getFullUrl()` (absolute URL).
- `InertiaRenderingOptions` is created with `InertiaRenderingOptions.builder(component, url)`; `withPartialComponent`
  is removed.
- `InertiaRenderer.location(url)` became `location(request, url)`.
- `TemplateRenderingException(String)` takes a message; use `TemplateRenderingException.notFound(path)` for missing
  templates.

## Spring

- `InertiaConfigurationProperties` exposes getters and setters instead of package-private fields.
- The default `encryptHistory` value is now read from `inertia.encrypt-history`.
- The `Inertia` bean is built from an `InertiaRenderer` bean, which can be replaced.
- An `InertiaFilter` bean is registered in servlet applications; disable it with `inertia.filter.enabled=false`.

## Ktor

- Flash data, errors and redirect flags need the `Sessions` plugin with an `InertiaSession` registered, or a custom
  `InertiaFlashStore`.
- The plugin checks asset versions and rewrites redirects of every call; disable it with `middleware = false`.
