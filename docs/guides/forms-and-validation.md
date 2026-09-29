# Forms and validation

A form submission in Inertia follows the classic server-side pattern: the client posts the form, the server validates
it, and answers with a redirect. On success the redirect goes to the next page, often with a flash message. On failure
it goes back to the form, and the validation errors are sent with that page.

Flash data, errors and the `preserveFragment` and `clearHistory` flags survive the redirect in the session. They are
sent with the next rendered page, including a page rendered for a prefetch request, then cleared.

## Session setup

**Spring Boot** uses the `HttpSession`. Nothing to configure.

**Ktor** stores this data through an `InertiaFlashStore`. The default one relies on the `Sessions` plugin with an
`InertiaSession` registered:

```kotlin
install(Sessions) {
    cookie<InertiaSession>("INERTIA_SESSION", SessionStorageMemory()) {
        serializer = InertiaSession.Serializer
    }
}
```

Use a shared `SessionStorage` when running several instances, or set `flashStore` in the plugin configuration to
keep the data elsewhere.

## Flash data

```java
@PostMapping("/records")
public ResponseEntity<String> store(@RequestBody RecordForm form) {
    records.save(form);
    inertia.flash("message", "Record created");
    return inertia.redirect("/records");
}
```

```kotlin
post("/records") {
    recordRepository.save(call.receive<RecordForm>())
    inertia.flash("message", "Record created")
    inertia.redirect("/records")
}
```

`flash(Map)` sets several entries at once. The client exposes flash data through the `inertia:flash` event. See the [Inertia documentation](https://inertiajs.com/docs/v3/data-props/flash-data).

## Validation errors

Every page has an `errors` prop, `{}` by default. Set errors before redirecting back; they are sent with the next page,
namespaced under the error bag requested by the client (`useForm` with an `errorBag`), if any.

**Spring Boot** accepts a `BindingResult` (or any `Errors`) directly:

```java
@PostMapping("/users")
public ResponseEntity<String> store(@Valid @ModelAttribute UserForm form, BindingResult result) {
    if (result.hasErrors()) {
        inertia.errors(result); // first message of each field
        return inertia.back();
    }

    users.create(form);
    return inertia.redirect("/users");
}
```

**Ktor** takes a map of messages:

```kotlin
post("/users") {
    val form = call.receive<UserForm>()
    val errors = validate(form) // e.g. mapOf("name" to "The name field is required.")

    if (errors.isNotEmpty()) {
        inertia.errors(errors)
        return@post inertia.back()
    }

    userRepository.create(form)
    inertia.redirect("/users")
}
```

### Every message per field

By default each field gets one message. To send all of them as a list:

- Spring: set `inertia.validation.all-errors=true`, or call `inertia.errors(ValidationErrors.allMessages(result))`.
- Ktor: pass lists of messages to `inertia.errors`.

If you generate [TypeScript types](../typescript.md), set `errorValueType.set(ErrorValueType.StringArray)` to match.

See the [Inertia documentation](https://inertiajs.com/docs/v3/the-basics/validation).

## Precognition

Precognition lets the client validate a form as the user types, without running the action. The request carries a
`Precognition: true` header; answer it with `inertia.precognition(...)`, which returns `204 No Content` when the
validated fields have no errors and `422 Unprocessable Entity` with the errors otherwise.

**Spring Boot.** Register `PrecognitionFilter` for these routes so their responses carry `Vary: Precognition`:

```java
@PostMapping("/users")
public ResponseEntity<String> store(@Valid @RequestBody UserForm form, BindingResult result) {
    if (inertia.isPrecognitive()) {
        return inertia.precognition(result);
    }
    /* ... */
}
```

**Ktor.** Install the route-scoped `Precognition` plugin:

```kotlin
route("/users") {
    install(Precognition)

    post {
        val errors: Map<String, List<String>> = validate(call.receive<UserForm>())

        if (inertia.isPrecognitive) {
            return@post inertia.precognition(errors)
        }
        /* ... */
    }
}
```

See the [Inertia documentation](https://inertiajs.com/docs/v3/the-basics/forms#precognition).

## CSRF protection

The Inertia client reads the `XSRF-TOKEN` cookie and sends it back in the `X-XSRF-TOKEN` header.

**Spring Security** stores the token in that cookie with:

```java
http.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
```

Spring Security 6 writes the cookie only once the token is used; follow its
[single-page application guide](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa)
to load it on every request.

**Ktor** has no built-in token of this kind. Set the cookie yourself and check the header on state-changing requests,
or rely on the `CSRF` plugin's origin checks.

See the [Inertia documentation](https://inertiajs.com/docs/v3/security/csrf-protection).
