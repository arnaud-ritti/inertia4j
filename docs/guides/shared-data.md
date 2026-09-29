# Shared data

Shared props are added to every Inertia response: the signed-in user, the application name, feature flags. The props
passed to `render` take precedence when keys collide.

## Sharing with every response

**Spring Boot.** Every `SharedDataProvider` bean (`io.github.inertia4j.springshared`) is picked up automatically:

```java
@Component
public class AppSharedData implements SharedDataProvider {
    private final UserService users;

    public AppSharedData(UserService users) {
        this.users = users;
    }

    @Override
    public Map<String, Object> share(HttpServletRequest request) {
        return Map.of(
            "appName", "My App",
            "user", (Supplier<Object>) () -> users.current(request) // computed only when sent
        );
    }
}
```

**Ktor.** Register a provider in the plugin configuration. It is a suspending function receiving the call:

```kotlin
install(Inertia) {
    share { call ->
        mapOf(
            "appName" to "My App",
            "user" to { currentUser(call) },
        )
    }
}
```

Lazy values and [prop types](props.md) behave as they do in `render`: an `Inertia.optional(...)` shared prop is only
sent when a partial reload asks for it.

## Sharing with the current request

From a filter, an interceptor or the controller itself:

```java
inertia.share("flash", "Record saved!");
inertia.share("auth.user", user);                 // dotted keys set nested props
inertia.shareOnce("countries", countries::findAll); // shared once prop
```

```kotlin
inertia.share("auth.user", user)
inertia.shareOnce("countries") { countryRepository.findAll() }
```

In Spring, `inertia.share(request, key, value)` targets an explicit request, useful outside the request thread.

## Typed shared data

A class annotated with `@InertiaShared` describes shared props for the [TypeScript generator](../typescript.md):

```java
@InertiaShared
public record AppShared(@Nullable User user, String appName) {}

@Bean
TypedSharedDataProvider appShared(UserService users) {
    return request -> new AppShared(users.current(request), "My app");
}
```

```kotlin
install(Inertia) {
    shareTyped { call -> AppShared(call.currentUser(), "My app") }
}
```

## Shared prop keys

Page objects list the top-level keys of shared props in `sharedProps`, so the client can carry them over during
instant visits. Disable it with
`inertia.expose-shared-prop-keys=false` (Spring) or `exposeSharedPropKeys = false` (Ktor).

See the [Inertia documentation](https://inertiajs.com/docs/v3/data-props/shared-data).
