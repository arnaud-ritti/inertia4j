# Redirects

Inertia distinguishes two kinds of redirects:

- `redirect(location)` sends the client to another Inertia page. The client follows it with an Inertia visit.
- `location(url)` sends the client to a page outside Inertia (a file download, an OAuth provider, another site). For
  Inertia requests it answers `409 Conflict` with an `X-Inertia-Location` header, and the client performs a full
  `window.location` visit. For regular requests it's a plain `302`.

`back()` redirects to the `Referer` of the request.

**Spring Boot**

```java
@PostMapping("/records")
public ResponseEntity<String> store(@RequestBody RecordForm form) {
    records.save(form);
    return inertia.redirect("/records");
}

@GetMapping("/login/github")
public ResponseEntity<String> github() {
    return inertia.location(oauth.authorizationUrl());
}
```

**Ktor**

```kotlin
post("/records") {
    recordRepository.save(call.receive<RecordForm>())
    inertia.redirect("/records")
}

get("/login/github") {
    inertia.location(oauth.authorizationUrl())
}
```

## Status codes

Browsers replay the original method when following a `302` after `PUT`, `PATCH` or `DELETE`, so `redirect` answers
those requests with `303 See Other`, which the client follows with a `GET`.

## URL fragments

An XHR can't follow a redirect to a URL with a fragment (`/records/1#comments`) and keep the fragment. For Inertia
requests, such redirects become a `409 Conflict` with an `X-Inertia-Redirect` header, and the client visits the
location itself.

To keep the fragment of the current URL on the page you redirect to, call `inertia.preserveFragment()` before
redirecting. The flag is stored in the session until the next page is rendered.

## Redirects that bypass Inertia

The rules above also apply to responses your code doesn't build with `inertia.redirect`, such as Spring's
`redirect:` view names, `response.sendRedirect(...)` or Ktor's `call.respondRedirect(...)`. The Spring
`InertiaFilter` and the Ktor plugin:

- turn a `302` answering a `PUT`, `PATCH` or `DELETE` Inertia request into a `303`;
- turn a redirect to a location with a fragment into `409 Conflict` with `X-Inertia-Redirect`;
- answer a `GET` Inertia request carrying an outdated [asset version](asset-versioning.md) with `409` before your
  handler runs, keeping flash data for the next request;
- redirect an Inertia request answered with an empty `200 OK` back to its `Referer` (or `/`);
- add `Vary: X-Inertia` to every response, so caches keep HTML and JSON apart.

Disable this with `inertia.filter.enabled=false` (Spring) or `middleware = false` (Ktor). You then have to apply these
rules yourself.

See the [Inertia documentation](https://inertiajs.com/docs/v3/the-basics/redirects).
