# Testing

Inertia4J ships assertions for Inertia responses, modeled on Inertia Laravel's `assertInertia`. They read both full
page visits (the page object embedded in the HTML document) and Inertia requests (the page object JSON), so the same
assertions work whatever the request headers.

## Spring Boot (MockMvc)

`InertiaResultMatchers` needs `spring-test`, which `spring-boot-starter-test` already brings:

```java
import static io.github.inertia4j.springshared.testing.InertiaResultMatchers.inertia;
import static io.github.inertia4j.springshared.testing.InertiaResultMatchers.inertiaPage;

@WebMvcTest(UsersController.class)
class UsersControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void listsUsers() throws Exception {
        mockMvc.perform(get("/users"))
            .andExpect(status().isOk())
            .andExpect(inertia(page -> page
                .component("Users/Index")
                .url("/users")
                .has("users", 3, user -> user
                    .where("id", 1)
                    .where("name", "Jane")
                    .missing("password"))
                .where("filters.search", "")
                .hasDeferredProp("permissions")
                .hasNoErrors()));
    }

    @Test
    void readsAProp() throws Exception {
        String name = (String) inertiaPage(mockMvc.perform(get("/users")).andReturn()).prop("users.0.name");

        assertEquals("Jane", name);
    }
}
```

Pass the `MockMvc` instance to follow up with more requests. They carry over the session and cookies of the first one:

```java
mockMvc.perform(get("/users"))
    .andExpect(inertia(mockMvc, page -> page
        .missing("permissions")
        .loadDeferredProps(deferred -> deferred.has("permissions", 2))
        .reloadOnly("users", reloaded -> reloaded.missing("filters"))));
```

## Ktor (testApplication)

```kotlin
import io.github.inertia4j.ktor.testing.assertInertia
import io.github.inertia4j.ktor.testing.inertiaPage

class UsersTest {
    @Test
    fun `lists users`() = testApplication {
        application { module() }

        client.get("/users").assertInertia {
            component("Users/Index")
            has("users", 3) { user -> user.where("id", 1).where("name", "Jane").missing("password") }
            hasDeferredProp("permissions")
            hasNoErrors()

            loadDeferredProps { deferred -> deferred.has("permissions", 2) }
            reloadOnly("users") { reloaded -> reloaded.missing("filters") }
        }

        val name = client.get("/users").inertiaPage().prop("users.0.name")
    }
}
```

Follow-up requests reuse the test client and carry over the cookies of the original request and response, unless the
client installs `HttpCookies`. When sending Inertia requests yourself, send `X-Inertia-Version` along with
`X-Inertia`, or the response is a `409` version conflict.

## Assertions

Paths are dotted, with numeric segments indexing arrays (`users.0.name`). Numbers are compared by value, so
`where("total", 3L)` matches `3`. Failures throw an `AssertionError`.

| Group          | Methods                                                                                                   |
|----------------|-----------------------------------------------------------------------------------------------------------|
| Page           | `component`, `url`, `version`, `encryptHistory`, `clearHistory`                                           |
| Props          | `has`, `hasAll`, `hasAny`, `missing`, `missingAll`, `count`                                               |
| Values         | `where`, `whereNot`, `whereAll`, `whereContains`, `whereMatches`                                          |
| Nested scopes  | `has(path, count, scope)`, `first`, `each`                                                                |
| Flash / errors | `hasFlash`, `missingFlash`, `hasError`, `missingError`, `hasNoErrors`                                     |
| Metadata       | `hasDeferredProp`, `missingDeferredProp`, `hasMergeProp`, `hasPrependProp`, `hasDeepMergeProp`, `hasOnceProp`, `hasScrollProp`, `hasSharedProp` |
| Follow-ups     | `reload`, `reloadOnly`, `reloadExcept`, `loadDeferredProps`                                               |

## Other test clients

Both adapters wrap `io.github.inertia4j.core.testing.AssertableInertia`, which works with any HTTP client. See
[Extending Inertia4J](extending.md#testing).
