# Responses and templates

## Rendering a page

`render` takes the name of the component to render on the client and its props. It returns a full HTML document on
the first visit and the page object as JSON on Inertia visits.

**Spring Boot.** Inject the `Inertia` bean (`dev.arkoder.inertia4j.springboot3.Inertia`, or `springboot4`) and return
the `ResponseEntity<String>` it builds:

```java
@RestController
public class RecordsController {
    private final Inertia inertia;
    private final RecordRepository records;

    public RecordsController(Inertia inertia, RecordRepository records) {
        this.inertia = inertia;
        this.records = records;
    }

    @GetMapping("/records")
    public ResponseEntity<String> index() {
        return inertia.render("Records/Index", Map.of("records", records.findAll()));
    }
}
```

**Ktor.** Inside a route, `inertia` is available once the plugin is installed. Props are passed as pairs:

```kotlin
get("/records") {
    inertia.render("Records/Index", "records" to recordRepository.findAll())
}
```

Calling `inertia` in a route without installing the plugin throws `PluginNotInstalledException`.

## Typed props

Instead of a map, you can render an object whose class is annotated with `@InertiaPage`. The component name comes from
the annotation and the object's properties become the props:

```java
@InertiaPage("Records/Index")
public record RecordsIndexProps(List<Record> records, InertiaProp<List<Stat>> stats) {}

return inertia.render(new RecordsIndexProps(records, Inertia.defer(statsService::compute)));
```

```kotlin
@InertiaPage("Records/Index")
data class RecordsIndexProps(val records: List<Record>, val stats: InertiaProp<List<Stat>>)

inertia.render(RecordsIndexProps(records, InertiaProps.defer { stats() }))
```

Typed props are the input of the [TypeScript generator](typescript.md), which keeps frontend page types in sync
with the backend.

## Status and history options

**Spring Boot** takes an `Inertia.Options` value as the last argument:

```java
import dev.arkoder.inertia4j.springboot3.Inertia.Options;

return inertia.render("Errors/NotFound", Map.of(), Options.status(404));
return inertia.render("Account/Show", props, Options.encryptHistory().clearHistory());
```

**Ktor** uses named arguments:

```kotlin
inertia.render("Errors/NotFound", status = HttpStatusCode.NotFound)
inertia.render("Account/Show", "user" to user, encryptHistory = true, clearHistory = true)
```

### History encryption

`encryptHistory` asks the client to encrypt the page state it stores in the browser history, so sensitive data can't
be read back after logout. `clearHistory` rotates the encryption key, making previous entries unreadable. See the
[Inertia documentation](https://inertiajs.com/docs/v3/security/history-encryption).

To encrypt every page by default, set `inertia.encrypt-history=true` (Spring) or `encryptHistory = true` in the Ktor
plugin, then opt out per response with `Options.encryptHistory(false)` / `encryptHistory = false`.

`clearHistory` has no default. To clear history on the page shown after a redirect (typically after logout), call
`inertia.clearHistory()` before redirecting.

## The root template

First visits are answered with the HTML template at `classpath:templates/app.html` (`inertia.template-path` /
`templatePath`). The default renderer replaces these placeholders:

| Placeholder                          | Replaced with                                                                                                  |
|--------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `@InertiaApp@`                       | The page object `<script data-page="app" type="application/json">` followed by `<div id="app"></div>`          |
| `@InertiaHead@`                      | The `<head>` elements rendered by the [SSR server](ssr.md); empty without SSR                                  |
| `@InertiaHead@…@EndInertiaHead@`     | The SSR `<head>` elements, or the enclosed fallback (e.g. a default `<title>`) when the page isn't server-rendered |
| `@Vite(entry, …)@`                   | Script and stylesheet tags of the entries, from the dev server or the build. See [Vite](vite.md)            |
| `@ViteReactRefresh@`                 | The React Fast Refresh preamble in development; nothing in production                                          |
| `@ViteAsset(path)@`                  | The URL of a file processed by Vite                                                                            |

The id of the root element is `app` by default (`inertia.root-id` / `rootId`). Change it on both sides by passing the
same `id` to `createInertiaApp`.

The template is loaded once, when the application starts, so restart after editing it. Templates still containing the 1.x `@PageObject@` placeholder are rejected at
startup; see the [migration guide](../migration-3.0.md#html-template).

To use a template engine such as Thymeleaf or Pebble instead, implement a
[custom `TemplateRenderer`](extending.md#html-template).
