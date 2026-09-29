# TypeScript Types for Props — Design

Date: 2026-09-29
Status: Approved for planning
Roadmap item: 1.1 — "Generate TypeScript types for props"

## Goal

Let frontend code consuming Inertia4J responses be type-checked against the backend. Generate a `.d.ts` file from
annotated JVM classes that plugs into Inertia's own TypeScript integration:

- page props, usable with `usePage<T>()` and as page component props;
- shared props, via declaration merging of `InertiaConfig.sharedPageProps` in `@inertiajs/core`;
- form / request body types, usable with `useForm<T>()`, `<Form<T>>` and `router.post<T>()`;
- `InertiaConfig.errorValueType`.

Success: a Spring Boot or Ktor app (Java or Kotlin) annotates its props classes, runs one Gradle task, and gets a
deterministic `inertia.d.ts` that type-checks with `tsc` and matches the JSON actually sent by the adapter.

## Decisions

| Topic | Decision |
|---|---|
| Source of types | Role annotations on user classes: `@InertiaPage`, `@InertiaShared`, `@InertiaForm` |
| Generation | Build time, Gradle plugin scanning compiled classes (Java + Kotlin) |
| Runtime API | Opt-in typed overloads; existing `Map`-based API unchanged |
| Deferred / merge props | `DeferredProp<T>` and `MergeProp<T>` become generic so fields carry the wrapped type |

## Modules

### `inertia4j.typescript-annotations` (new)

Annotations only, no dependencies, so shared DTO modules need not depend on core.

- `@InertiaPage(String value)` — class describes the props of the page component `value`.
- `@InertiaShared` — class describes (part of) the shared props.
- `@InertiaForm` — class describes a request body sent by the frontend.
- `@TypeScriptName(String value)` — overrides the emitted interface / type name.

All are `@Retention(RUNTIME)`, `@Target(TYPE)`.

### `inertia4j.typescript` (new)

Pure Java generator library, no Gradle dependency (keeps a future Maven plugin or CLI possible).

Entry point: `TypeScriptGenerator.generate(GeneratorOptions options, ClassLoader classLoader, List<Path> classpathRoots): String`.

Units:

- `ClassScanner` — walks class directories and jars, keeps classes under the configured package roots, loads them
  and returns those annotated with one of the role annotations. Hand-written, no ClassGraph.
- `TypeModelBuilder` — builds a type model (interfaces, enums, generic parameters, properties with nullability /
  optionality) from the scanned classes, discovering referenced types transitively.
- `PropertyIntrospector` — lists serialized properties of a class (record components, bean getters, Kotlin
  properties) with their names after naming rules, in declaration order. Shared with the runtime `PropsExtractor`
  rules (see below) so keys match.
- `KotlinNullability` — reads `kotlin.Metadata` via `kotlinx-metadata-jvm`; only Kotlin dependency.
- `TypeScriptWriter` — renders the model to the `.d.ts` text, deterministically.

### `inertia4j.typescript-gradle-plugin` (new)

Plugin id `io.github.inertia4j.typescript`. Details in "Gradle integration".

### Changes to existing modules

**Core (`inertia4j.core`)**

- `DeferredProp` → `DeferredProp<T> extends MergeableProp<DeferredProp<T>>`; `MergeProp` → `MergeProp<T>`.
  `InertiaProps.defer(Supplier<T>)` returns `DeferredProp<T>`, `merge(T)` / `deepMerge(T)` return `MergeProp<T>`.
  Raw-type callers keep compiling. `PropsResolver` behaviour unchanged.
- New `PropsExtractor.toMap(Object props): Map<String, Object>` — converts a record, bean or Kotlin class to an
  ordered map (`LinkedHashMap`, declaration order). `DeferredProp`, `MergeProp` and `Supplier` values are left as-is
  so `PropsResolver` handles them. Honors `@JsonProperty` / `@JsonIgnore` (matched by annotation simple name,
  Jackson 2 and 3) and the same naming option as the generator (see "Property naming"). Reflection failures are
  wrapped in `InertiaException`.
- Core depends on `inertia4j.typescript-annotations` (to read `@InertiaPage`).

**Spring (`spring-shared`, `spring-boot-3`, `spring-boot-4`)**

- `AbstractInertia.render(Object pageProps)` and `render(Object pageProps, InertiaSpringRendererOptions options)`.
  Component name read from `@InertiaPage` on the class; missing annotation → `IllegalArgumentException`.
  Props converted with `PropsExtractor.toMap`.
- New `TypedSharedDataProvider` functional interface: `Object share(HttpServletRequest request)` returning an
  `@InertiaShared` object. Autoconfiguration collects these beans alongside `SharedDataProvider` beans (same
  `@Order` semantics) and converts results with `PropsExtractor.toMap`. `SharedDataProvider` is unchanged.

**Ktor**

- `inertia.render(pageProps: Any)` and overload with options, same semantics as Spring.
- `InertiaKtorConfiguration.shareTyped(provider: suspend (ApplicationCall) -> Any)` — result converted with
  `PropsExtractor.toMap`. Existing `share` unchanged.

## Type mapping

### Built-in types

| JVM | TypeScript |
|---|---|
| `String`, `char`/`Character`, `UUID`, `URI`, `URL` | `string` |
| `int`, `long`, `short`, `byte`, `double`, `float` and boxed, `BigDecimal`, `BigInteger` | `number` |
| `boolean` / `Boolean` | `boolean` |
| `Instant`, `LocalDate`, `LocalDateTime`, `OffsetDateTime`, `ZonedDateTime`, `LocalTime`, `Duration` | `string` (ISO, Jackson default with `WRITE_DATES_AS_TIMESTAMPS` off) |
| `Collection<T>`, `T[]`, Kotlin `List<T>` / `Set<T>` | `T[]` |
| `byte[]` | `string` (base64) |
| `Map<K, V>` with `K` string, number or enum | `Record<K, V>` |
| `Optional<T>` | `T \| null` |
| `Object`, `JsonNode`, raw / unknown types | `unknown` |

### User types

- Records, beans (getters) and Kotlin classes → `export interface <SimpleName>`.
- Referenced types are emitted transitively, but only for classes under the configured package roots; others not
  in the built-in table map to `unknown` with a warning.
- Enums → string union `export type Status = 'Active' | 'Archived'`, using `name()` or the `@JsonValue` method result.
- Generic user types → generic interfaces (`export interface Page<T> { items: T[]; total: number }`), referenced
  with arguments (`Page<Record>`). Unresolvable type variables / wildcards → `unknown` with a warning.
- Inheritance flattened: an interface contains all inherited serialized properties; no `extends`.
- Name collision between two emitted types → fatal error naming both classes; fix with `@TypeScriptName`.

### Inertia wrappers

- `DeferredProp<T>` → optional property `name?: T` (absent on initial load).
- `MergeProp<T>`, `Supplier<T>`, Kotlin `() -> T` → `T`.
- Nesting unwraps: `DeferredProp<MergeProp<T>>` → `name?: T`.

### Nullability

- Java: nullable when annotated with any annotation whose simple name is `Nullable` (jspecify, JetBrains, Jakarta,
  Spring, …). With `nullableByDefault = true`, properties are nullable unless annotated `NonNull` / `NotNull` or
  declared in a `@NullMarked` scope. Default `nullableByDefault = false`: non-null unless annotated.
- Kotlin: from `kotlin.Metadata` (`String?` → nullable).
- Nullable → `name: T | null`. With `@JsonInclude(NON_NULL)` (or `NON_ABSENT` / `NON_EMPTY`) on the property or class
  → `name?: T`.

### Property naming

- Default: property / record component name.
- `@JsonProperty("x")` renames, `@JsonIgnore` skips (Jackson 2 and 3, matched by simple name; no Jackson dependency).
- Option `propertyNaming = Camel | Snake` applies a global strategy. No auto-detection of `ObjectMapper` config.
- The same rules are applied at runtime by `PropsExtractor`, which takes the naming option from adapter config
  (`inertia.property-naming` in Spring, `propertyNaming` in `InertiaKtorConfiguration`), defaulting to `Camel`.

## Output

Single file, deterministic: header comment, then enums and interfaces sorted by name, properties in declaration
order, then the page map and module augmentation.

```ts
// Generated by Inertia4J. Do not edit.
import '@inertiajs/core'

export type Status = 'Active' | 'Archived'
export interface Record { id: number; title: string; status: Status }
export interface Stat { label: string; value: number }
export interface User { id: number; name: string }

export interface RecordsIndexProps { records: Record[]; stats?: Stat[] }
export interface CreateRecordForm { title: string; status: Status }
export interface AppShared { user: User | null; appName: string }

export interface InertiaPages {
  'Records/Index': RecordsIndexProps
}

export type PageProps<C extends keyof InertiaPages> = InertiaPages[C]

declare module '@inertiajs/core' {
  export interface InertiaConfig {
    sharedPageProps: AppShared
    errorValueType: string
  }
}
```

- Several `@InertiaShared` classes → `sharedPageProps: A & B` (sorted by name).
- No `@InertiaShared` class → `sharedPageProps` omitted. `errorValueType` always emitted (`string` or `string[]`).
- Duplicate `@InertiaPage` component name → fatal error naming both classes.

Frontend usage: `usePage<PageProps<'Records/Index'>>()`, `useForm<CreateRecordForm>(...)`,
`export default function Index(props: RecordsIndexProps)`.

## Gradle integration

```kotlin
plugins { id("io.github.inertia4j.typescript") version "x.y.z" }

inertiaTypes {
    packages.set(listOf("com.example.app"))                      // required
    outputFile.set(file("src/main/frontend/types/inertia.d.ts")) // default: build/inertia/inertia.d.ts
    errorValueType.set(ErrorValueType.String)                    // String | StringArray
    propertyNaming.set(PropertyNaming.Camel)                     // Camel | Snake
    nullableByDefault.set(false)
}
```

- Requires the `java` plugin (Kotlin JVM applies it); applied via `pluginManager.withPlugin("java")`.
- `generateInertiaTypes`: depends on `classes`; inputs are the `main` runtime classpath and extension properties;
  output is `outputFile`. `@CacheableTask`. Runs the generator through the Worker API with classloader isolation.
- `checkInertiaTypes`: generates into a temp file, compares with `outputFile`, fails with
  "Inertia types are out of date, run `generateInertiaTypes`" on difference. Not wired to `check` automatically.
- Missing `packages` → configuration error.

## Error handling

Fatal (`GenerationException`, message names class, property and rule):

- duplicate emitted type name;
- duplicate `@InertiaPage` component name;
- blank `@InertiaPage` value;
- `Map` key type that is not string, number or enum.

Warnings, generation continues:

- type outside package roots → `unknown`;
- unresolvable type variable / wildcard → `unknown`;
- class failing to load (`NoClassDefFoundError`, `LinkageError`) → skipped.

Runtime:

- `render(Object)` without `@InertiaPage` → `IllegalArgumentException`;
- `PropsExtractor` reflection failure → `InertiaException`.

## Testing

TDD throughout.

- `inertia4j.typescript`:
  - one unit test per mapping rule, with Java and Kotlin fixtures in test sources;
  - golden-file tests comparing full output to `src/test/resources/expected/*.d.ts`;
  - `tsc --noEmit` test on golden output with a stub `@inertiajs/core`, skipped when `npx` is unavailable; CI gets
    a Node setup step so it runs there.
- Core: `PropsExtractor` order, `@JsonProperty` / `@JsonIgnore`, naming option, wrappers preserved; existing
  `InertiaRendererTest` stays green after generics change.
- Adapters (Spring Boot 3, Spring Boot 4, Ktor): `render(pageProps)` page object JSON; typed shared provider merged
  into props; missing `@InertiaPage` error.
- Gradle plugin: TestKit `GradleRunner` functional tests on a sample project — file generated, up-to-date on second
  run, `checkInertiaTypes` fails on drift, missing `packages` error.

## Documentation

- New `docs/typescript.md`: annotations, Gradle setup, frontend usage (`usePage`, `useForm`, page component props),
  pnpm `public-hoist-pattern[]=@inertiajs/core` note, `tsconfig` `include` of `.d.ts`.
- Adapter READMEs: short "Typed props" section linking to it.
- `docs/roadmap.md`: tick the 1.1 item.

## Out of scope (v1)

- Maven plugin (generator library keeps it possible).
- `flashDataType` (no flash support in adapters), `layoutProps`, `namedLayoutProps`.
- Route / URL generation.
- Auto-detection of a global Jackson naming strategy.
- `extends` in output; sealed classes as discriminated unions.
- kotlinx.serialization (`@Serializable`, `@SerialName`) naming.
