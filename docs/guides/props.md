# Props

Props are the data a page component receives. Beyond plain values, Inertia4J supports the prop types of the Inertia v3
protocol, which control *when* a prop is computed and *how* the client combines it with what it already holds.

Prop types are created with static factories:

| Spring Boot                  | Ktor and plain Java                                  |
|------------------------------|------------------------------------------------------|
| `Inertia.defer(...)`         | `InertiaProps.defer { ... }`                         |
| `Inertia.optional(...)`, …   | `InertiaProps.optional { ... }`, …                   |

`InertiaProps` lives in `io.github.inertia4j.core`; the `Inertia` bean methods delegate to it. Each factory returns an
`InertiaProp<T>`, whose modifiers compose (`Inertia.defer(...).merge().once()`).

## Lazy props

Wrap a value in a `Supplier` (Java) or a function (Kotlin) to compute it only when it is sent:

```java
Map.of("users", (Supplier<Object>) () -> userRepository.findAll())
```

```kotlin
"users" to { userRepository.findAll() }
```

Lazy props are still sent on every full visit, but skipped by partial reloads that don't ask for them.

## Partial reloads

The client can reload a subset of the props of the current page:

```ts
router.reload({ only: ['users'] })
router.reload({ except: ['companies'] })
router.reload({ only: ['auth.user'] }) // nested props use dotted paths
```

Only the requested props are resolved. Two prop types refine this:

| Factory        | Full visit | Partial reload                  |
|----------------|------------|---------------------------------|
| `optional(fn)` | Not sent   | Sent only when requested        |
| `always(v)`    | Sent       | Sent even when not requested    |

```java
return inertia.render("Users/Index", Map.of(
    "users", (Supplier<Object>) () -> userRepository.findAll(),
    "companies", Inertia.optional(() -> companyRepository.findAll()),
    "auth", Inertia.always(currentUser)
));
```

```kotlin
inertia.render(
    "Users/Index",
    "users" to { userRepository.findAll() },
    "companies" to InertiaProps.optional { companyRepository.findAll() },
    "auth" to InertiaProps.always(currentUser),
)
```

A partial reload that targets another component than the one rendered returns a full response.
See the [Inertia documentation](https://inertiajs.com/docs/v3/data-props/partial-reloads).

## Deferred props

Deferred props are left out of the first response. The client renders the page, then fetches them in a follow-up
request. Props sharing a group name are fetched together:

```java
return inertia.render("Records/Index", Map.of(
    "records", recordRepository.findAll(),
    "permissions", Inertia.defer(() -> permissionRepository.findAll()),
    "teams", Inertia.defer(() -> teamRepository.findAll(), "attributes"),
    "projects", Inertia.defer(() -> projectRepository.findAll(), "attributes")
));
```

```kotlin
inertia.render(
    "Records/Index",
    "records" to recordRepository.findAll(),
    "permissions" to InertiaProps.defer { permissionRepository.findAll() },
    "teams" to InertiaProps.defer({ teamRepository.findAll() }, "attributes"),
    "projects" to InertiaProps.defer({ projectRepository.findAll() }, "attributes"),
)
```

On the client, wrap the part of the page that needs them in `<Deferred data="permissions">`.

### Rescuing failures

A deferred prop that may fail can be rescued with `.rescue()`. Its exception is reported, the other props are still
sent, and the client renders the `rescue` slot of `<Deferred>`:

```java
"permissions", Inertia.defer(() -> permissionService.fetch()).rescue()
```

Spring logs the exception; Ktor passes it to `exceptionReporter` (logging by default). `rescue()` only applies to
deferred props; exceptions of other props propagate.

See the [Inertia documentation](https://inertiajs.com/docs/v3/data-props/deferred-props).

## Merging props

By default, a prop returned by a partial reload replaces the client's value. Merge props are combined with it instead,
which is how "load more" buttons work:

```java
return inertia.render("Records/Index", Map.of(
    "records", Inertia.merge(recordPage.getContent()),                   // append items
    "notifications", Inertia.merge(notifications).prepend(),             // prepend items
    "feed", Inertia.merge(feed).append("data").prepend("messages"),      // merge nested arrays
    "posts", Inertia.merge(posts).matchOn("id"),                         // update matching items in place
    "users", Inertia.merge(users).append("data", "id"),                  // append to data, match on data.id
    "conversations", Inertia.deepMerge(conversations).matchOn("data.id") // merge nested objects recursively
));
```

The Ktor API is identical with `InertiaProps.merge(...)` and `InertiaProps.deepMerge(...)`. To make the value of a
merge prop lazy in Kotlin, wrap it in `Supplier { ... }`.

Merging combines with deferral: `Inertia.defer(() -> ...).merge()` or `.deepMerge()`. When the client resets a prop
(`router.reload({ reset: ['records'] })`), it is sent without merge instructions.

See the [Inertia documentation](https://inertiajs.com/docs/v3/data-props/merging-props).

## Once props

Once props are resolved a single time and remembered by the client, which reuses the value on later pages that
declare the same prop:

```java
return inertia.render("Billing/Plans", Map.of(
    "plans", Inertia.once(() -> planRepository.findAll()),
    "countries", Inertia.once(() -> countryRepository.findAll())
        .key("countries")               // share the remembered value with other pages
        .expiresIn(Duration.ofHours(1)) // or .until(Instant)
));
```

```kotlin
inertia.render(
    "Billing/Plans",
    "plans" to InertiaProps.once { planRepository.findAll() },
    "countries" to InertiaProps.once { countryRepository.findAll() }
        .key("countries")
        .expiresIn(Duration.ofHours(1)),
)
```

`.fresh()` sends a new value even when the client remembers the prop. Once combines with other types, e.g.
`Inertia.defer(() -> ...).once()`. Shared props can be once props too: `inertia.shareOnce(key, supplier)`.

See the [Inertia documentation](https://inertiajs.com/docs/v3/data-props/once-props).

## Infinite scroll

Scroll props hold one page of items under a `data` key, merged with the items the client already holds, plus the
pagination state read by the `<InfiniteScroll>` component:

```java
Page<Post> page = postRepository.findAll(pageable);

return inertia.render("Posts/Index", Map.of(
    "posts", Inertia.scroll(
        Map.of("data", page.getContent()),
        ScrollMetadata.forPage(page.getNumber() + 1, page.hasNext())
    )
));
```

```kotlin
val page = postRepository.page(number, size)

inertia.render(
    "Posts/Index",
    "posts" to InertiaProps.scroll(mapOf("data" to page.items), ScrollMetadata.forPage(number, page.hasNext)),
)
```

- `ScrollMetadata.of(pageName, previous, next, current)` describes cursor pagination.
- `.wrapper("items")` changes the key holding the items.
- `Inertia.scroll(() -> ..., page -> metadata).defer()` loads the first page after the initial render.

See the [Inertia documentation](https://inertiajs.com/docs/v3/data-props/infinite-scroll).

## Summary

| Factory / modifier            | Sent on full visit | Sent on partial reload      | Client behavior                  |
|-------------------------------|--------------------|-----------------------------|----------------------------------|
| plain value                   | Yes                | When requested              | Replace                          |
| `Supplier` / `() -> T`        | Yes (computed)     | When requested              | Replace                          |
| `optional`                    | No                 | When requested              | Replace                          |
| `always`                      | Yes                | Always                      | Replace                          |
| `defer`                       | No                 | Fetched after render        | Replace                          |
| `merge` / `deepMerge`         | Yes                | When requested              | Merge with current value         |
| `once`                        | First time only    | When requested              | Remembered across pages          |
| `scroll`                      | Yes                | When requested              | Merge pages, track pagination    |
