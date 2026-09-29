# TypeScript types

Inertia4J can generate a TypeScript declaration file from your props classes, so your pages, shared data and forms
are type-checked against the backend.

## 1. Describe your props with classes

Add the annotations (already included by the Spring and Ktor adapters):

| Annotation | Put it on | Generated |
|---|---|---|
| `@InertiaPage("Users/Show")` | the props of a page | an interface + an entry in `InertiaPages` |
| `@InertiaShared` | the props shared with every page | `InertiaConfig.sharedPageProps` |
| `@InertiaForm` | a request body sent by the frontend | an interface |
| `@TypeScriptName("UserDto")` | any class | renames its TypeScript type |

```java
@InertiaPage("Users/Index")
public record UsersIndexProps(List<User> users, DeferredProp<List<Stat>> stats) {}

@InertiaShared
public record AppShared(@Nullable User user, String appName) {}

@InertiaForm
public record CreateUserForm(String name, String email) {}
```

Render typed page props directly; the component name comes from `@InertiaPage`:

```java
return inertia.render(new UsersIndexProps(users, Inertia.defer(statsService::compute)));
```

```kotlin
inertia.render(UsersIndexProps(users, InertiaProps.defer { stats() }))
```

Share typed data with a `TypedSharedDataProvider` bean (Spring) or `shareTyped { … }` (Ktor):

```java
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

## 2. Generate the types with Gradle

```kotlin
plugins {
    id("io.github.inertia4j.typescript") version "1.0.0"
}

inertiaTypes {
    packages.set(listOf("com.example.app"))                      // required
    outputFile.set(file("src/main/frontend/types/inertia.d.ts")) // default: build/inertia/inertia.d.ts
    errorValueType.set(ErrorValueType.String)                    // or ErrorValueType.StringArray
    propertyNaming.set(PropertyNaming.Camel)                     // or PropertyNaming.Snake
    nullableByDefault.set(false)
}
```

Import `io.github.inertia4j.typescript.ErrorValueType` and `io.github.inertia4j.core.PropertyNaming` at the top of the
build script when you set these options.

- `./gradlew generateInertiaTypes` writes the file.
- `./gradlew checkInertiaTypes` fails when the file is out of date; add `tasks.check { dependsOn("checkInertiaTypes") }`
  to run it in CI.

If you set `propertyNaming` to `Snake`, also set `inertia.property-naming=snake` (Spring) or
`propertyNaming = PropertyNaming.Snake` (Ktor). The default page object serializer then applies Jackson's `SNAKE_CASE`
to the objects inside props, so nested objects are snake_case too, while the Inertia protocol keys (`component`,
`props`, `encryptHistory`, `deferredProps`, …) and the keys of maps stay as they are. If you provide your own
`PageObjectSerializer`, it must use the same naming strategy for the objects inside props.

## 3. Use the types in the frontend

Make sure `tsconfig.json` includes the generated file, e.g. `"include": ["src/main/frontend/**/*.ts", "src/main/frontend/**/*.d.ts"]`.

```tsx
import { usePage, useForm } from '@inertiajs/react'
import type { PageProps, UsersIndexProps, CreateUserForm } from '../types/inertia'

export default function Index({ users, stats }: UsersIndexProps) {
  const page = usePage<PageProps<'Users/Index'>>()
  page.props.appName // typed through sharedPageProps

  const form = useForm<CreateUserForm>({ name: '', email: '' })
}
```

With pnpm, hoist `@inertiajs/core` so the module augmentation resolves:

```ini
# .npmrc
public-hoist-pattern[]=@inertiajs/core
```

## Type mapping

| JVM | TypeScript |
|---|---|
| `String`, `char`, `UUID`, `URI`, `URL`, `java.time` types | `string` |
| numbers, `BigDecimal`, `BigInteger` | `number` |
| `boolean` | `boolean` |
| collections, arrays | `T[]` |
| `byte[]` | `string` |
| `Map<String, V>` / `Map<Long, V>` / `Map<Enum, V>` | `{ [key: string]: V }` / `{ [key: number]: V }` / `{ [key in Enum]?: V }` |
| `Optional<T>`, `@Nullable T`, Kotlin `T?` | `T \| null` (`field?: T` with `@JsonInclude(NON_NULL)`) |
| `DeferredProp<T>` | `field?: T` |
| `MergeProp<T>`, `Supplier<T>`, Kotlin `() -> T` | `T` |
| enums | string union, using `@JsonValue` when present |
| classes under `packages` | interfaces (inherited properties are flattened) |
| anything else | `unknown` (with a build warning) |

`@JsonProperty` and `@JsonIgnore` are honored. Global Jackson naming strategies are not detected: use `propertyNaming`.

### Kotlin classes

- Use `@get:JsonProperty` to rename a property: `@get:JsonProperty("user_name") val userName: String`. A bare
  `@JsonProperty` on a constructor property lands on the constructor parameter, which is not read, so the property
  keeps its default name. The same applies to `@JsonInclude`: write `@get:JsonInclude(...)`.
- A Boolean property named `isX` is generated like plain Jackson does, as `x` (`val isActive: Boolean` gives
  `active`). jackson-module-kotlin keeps `isActive`, so an `@InertiaForm` request body read by it would not match the
  generated type. Avoid `is`-prefixed names, or name them explicitly with `@get:JsonProperty`.
