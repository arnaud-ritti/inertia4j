# Route helpers

Inertia4J can generate a TypeScript file with one helper per backend route, so the frontend builds URLs from typed
functions instead of hard-coded strings. When a route changes on the server, the build tells you which pages to
update. The helpers follow the shape of Laravel's [Wayfinder](https://github.com/laravel/wayfinder), which Inertia's
`Link`, `Form` and `useForm` accept directly.

```tsx
import { Link, useForm } from '@inertiajs/react'
import { UsersController } from '../routes'

<Link href={UsersController.show({ id: user.id })}>{user.name}</Link>

const form = useForm({ name: '' })
form.submit(UsersController.store())
```

## Generating the file

The helpers are written by the [TypeScript Gradle plugin](typescript.md#2-generate-the-types-with-gradle):

```kotlin
inertiaTypes {
    packages.set(listOf("com.example.app"))
    routesOutputFile.set(file("src/main/frontend/routes.ts")) // default: build/inertia/routes.ts
    routePackages.set(listOf("com.example.app.web"))          // default: packages
}
```

- `./gradlew generateInertiaRoutes` writes the file.
- `./gradlew checkInertiaRoutes` fails when it is out of date; add `tasks.check { dependsOn("checkInertiaRoutes") }` to
  run it in CI.

Maven builds call `TypeScriptGenerator.generateRoutes(...)` the same way the [Maven guide](maven.md#typescript-types)
calls the types generator.

## Spring Boot

Every handler of a `@Controller` or `@RestController` class under `routePackages` gets a helper, named after the
controller and the method:

```java
@RestController
@RequestMapping("/users")
public class UsersController {
    @GetMapping
    public ResponseEntity<String> index() { /* ... */ }

    @GetMapping("/{id}")
    public ResponseEntity<String> show(@PathVariable long id) { /* ... */ }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable long id, @RequestBody UserForm form) { /* ... */ }
}
```

```ts
UsersController.index()               // { url: '/users', method: 'get' }
UsersController.show({ id: 1 })       // { url: '/users/1', method: 'get' }
UsersController.update({ id: 1 })     // { url: '/users/1', method: 'put' }
```

- Class-level `@RequestMapping` paths are prefixed; `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`,
  `@DeleteMapping` and `@RequestMapping` (with its `method` list, or every method when it has none) are supported.
- Regular expressions of path variables are dropped (`{id:\d+}` becomes `{id}`), and `{*path}` captures the rest of
  the path.
- Rename a controller with `@TypeScriptName("Users")`. Overloaded handlers are numbered (`search`, `search2`) with a
  build warning.
- Mappings using `*`, `**` or `${...}` placeholders are skipped with a warning, since no URL can be built for them.
- Controllers annotated with your own annotation meta-annotated with `@Controller` are picked up too.

## Ktor

Ktor routes are defined by code at runtime, so the plugin exports them to a manifest when the application starts, and
the generator reads it:

```kotlin
install(Inertia) {
    routeManifest = Path.of("build/inertia/routes.json")
}

routing {
    route("/users") {
        get { /* ... */ }.named("users.index")
        get("/{id}") { /* ... */ }.named("users.show")
        put("/{id}") { /* ... */ }
    }
}
```

```kotlin
// build.gradle.kts
inertiaTypes {
    routeManifests.from("build/inertia/routes.json")
}
```

```ts
users.index()          // { url: '/users', method: 'get' }
users.show({ id: 1 })  // { url: '/users/1', method: 'get' }
users.id.put({ id: 1 }) // unnamed: derived from the path and method
```

- `.named("users.show")` sets the dot-separated name. Unnamed routes are named after their path segments and method:
  `PUT /users/{id}` becomes `users.id.put`, `GET /` becomes `root.get`.
- `{id?}` optional parameters and `{path...}` tails are supported; wildcard (`*`) and regex routes are left out.
- Start the application once (or run a test that starts it) to write the manifest before generating. A missing
  manifest is reported as a warning. `Application.inertiaRouteManifest()` returns the manifest as a string, e.g. to
  write it from a test with `writeInertiaRouteManifest(path)`.

## Using the helpers

Each helper is callable and returns `{ url, method }` with the first method of the route. It also exposes:

| Member                              | Returns                                                                 |
|-------------------------------------|-------------------------------------------------------------------------|
| `show(args, options?)`              | `{ url, method }`                                                       |
| `show.url(args, options?)`          | The URL string                                                          |
| `show.get(args)`, `update.patch(args)`, … | `{ url, method }` for each method of the route                    |
| `show.form(args)`                   | `{ action, method }` for an HTML `<form>`                               |
| `show.definition`                   | `{ methods, url }` with the URL template                                |

Routes without parameters take only the options: `UsersController.index({ query: { page: 2 } })`.

### Parameters and query strings

Parameters are typed as `string | number` and URL-encoded; a missing required parameter is a type error and throws at
runtime. Optional parameters are dropped with their slash when missing. Query parameters go in `options.query`; arrays
repeat the key and `null` or `undefined` values are left out:

```ts
UsersController.index.url({ query: { page: 2, tags: ['a', 'b'] } }) // '/users?page=2&tags=a&tags=b'
```

### HTML forms

Browsers only send `GET` and `POST` forms, so `form()` turns other methods into a `POST` with a `_method` query
parameter: `UsersController.update.form({ id: 1 })` gives `{ action: '/users/1?_method=PUT', method: 'post' }`. The
server must translate it back:

- Spring Boot: set `spring.mvc.hiddenmethod.filter.enabled=true`.
- Ktor: handle `_method` yourself, or submit through Inertia (`form.submit(...)`), which sends the real method.

## Limitations

- URLs are relative to the application root: when the servlet context path isn't `/`, prefix them yourself.
- Spring functional endpoints (`RouterFunction`) aren't scanned.
- Query parameters aren't typed from `@RequestParam`.
