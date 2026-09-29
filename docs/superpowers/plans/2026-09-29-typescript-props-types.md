# TypeScript Types for Props Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate a `.d.ts` file from annotated JVM props classes (pages, shared props, forms) that plugs into Inertia's TypeScript integration, and let adapters render those typed classes directly.

**Architecture:** A dependency-free annotations module marks props classes. Core gains a reflective `PropertyIntrospector` (shared naming rules) and `PropsExtractor` (object → props map) used by the Spring and Ktor typed `render` overloads. A pure-Java generator library scans compiled classes in an isolated classloader, builds a TS type model, and writes the file; a Gradle plugin wraps it in `generateInertiaTypes` / `checkInertiaTypes` tasks.

**Tech Stack:** Java 11 (annotations, core), Java 17 (generator, plugin, Spring), Kotlin 2.1.20, `org.jetbrains.kotlin:kotlin-metadata-jvm`, Gradle 8.14.4 (`java-gradle-plugin`, TestKit), JUnit 5, Node `npx tsc` (optional test).

**Spec:** `docs/superpowers/specs/2026-09-29-typescript-props-types-design.md`

## Global Constraints

- Annotations and core stay on the Java 11 toolchain (`inertia4j.java-conventions`); no Java 12+ APIs in their `main` sources (records are detected reflectively).
- Generator and Gradle plugin use the Java 17 toolchain.
- Generator has no Jackson dependency: Jackson (2 and 3), jspecify and other annotations are matched **by name** (simple name for `JsonProperty`, `JsonIgnore`, `JsonInclude`, `JsonValue`, `Nullable`, `NonNull`, `NotNull`, `NullMarked`; fully-qualified name for Inertia4J annotations and wrappers).
- Existing `Map`-based APIs (`render(String, Map…)`, `SharedDataProvider`, Ktor `share`) keep their behaviour and signatures.
- New modules start at version `1.0.0`.
- Output is deterministic: declarations sorted by name, properties in declaration order, pages sorted by component name.
- Constants use PascalCase (project style: `DeferredProp.DefaultGroup`).
- Commit after every task, message ends with `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

**Deviations from the spec, decided while planning (keep them):**

1. `PropertyIntrospector` / `Property` / `PropertyNaming` live in **core** (not the generator) so runtime and generator share one implementation. The generator depends on core.
2. Maps render as index signatures (`{ [key: string]: V }`, `{ [key in Status]?: V }`) instead of `Record<K, V>`: a user type named `Record` would otherwise shadow the TS utility type.
3. `? extends X` wildcards map to `X` (Kotlin emits them for `List<out T>`); only `?` and `? super X` become `unknown` with a warning.
4. The Gradle task runs the generator in-process, with its own `URLClassLoader` whose parent is the platform classloader (full isolation of user classes) — no Worker API.
5. Interfaces are rendered one property per line (sample in Task 7), not on one line as in the spec's sketch.
6. User types named `InertiaPages` or `PageProps` are a fatal error (they would clash with generated helpers).

## Review Focus

1. **jspecify `@Nullable` is a `TYPE_USE` annotation** — it is invisible to `getAnnotations()`; it only appears on `getAnnotatedType()` / `getAnnotatedReturnType()`. Expect `@Nullable String nickname` in a record to generate `nickname: string | null`. Pinned in Task 3 (`typeUseNullable_isCollected`) and Task 9 (`nullability`).
2. **Kotlin `inertia.render("Component")` with no props must still hit the component-name overload**, not the new `render(pageProps: Any)`. Pinned in Task 6 (`render with component name only still renders`).
3. **Private / package-private nested records** (common in tests and controllers) must be readable at runtime — reflection must call `trySetAccessible`. Pinned in Task 4 and Task 5 (fixtures are `private record`).
4. **`inertia.property-naming=snake` (lower case) in `application.properties`** must bind to `PropertyNaming.Snake`. Pinned in Task 5 (`propertyNaming_bindsLowerCaseValue`).
5. **A user type named like a TS utility (`Record`) or a generated helper (`InertiaPages`)** must not silently produce broken TypeScript. Pinned in Task 7 (index-signature maps) and Task 9 (`reservedName_isRejected`).

---

## File Structure

```
settings.gradle.kts                                    (modify: include 3 modules)
build.gradle.kts                                       (modify: kotlin plugin apply false)
gradle/libs.versions.toml                              (modify: kotlin-metadata-jvm)
.github/workflows/ci.yml                               (modify: setup-node)

inertia4j.typescript-annotations/
  build.gradle.kts
  src/main/java/io/github/inertia4j/annotations/{InertiaPage,InertiaShared,InertiaForm,TypeScriptName}.java
  src/test/java/AnnotationsTest.java

inertia4j.core/
  build.gradle.kts                                     (modify: annotations api dep, test toolchain 17)
  src/main/java/io/github/inertia4j/core/
    DeferredProp.java, MergeProp.java, InertiaProps.java, PropsResolver.java   (modify: generics)
    PropertyNaming.java        enum Camel/Snake + apply()
    Property.java              one serialized property (name, type, annotations, reader)
    PropertyIntrospector.java  record/bean property discovery + naming rules
    PropsExtractor.java        object -> ordered props map, component name
  src/test/java/{PropertyNamingTest,PropertyIntrospectorTest,PropsExtractorTest}.java

inertia4j.spring-shared/src/main/java/io/github/inertia4j/springshared/
  AbstractInertia.java                  (modify: typed render, naming, typed shared)
  TypedSharedDataProvider.java          (create)
  InertiaConfigurationProperties.java   (modify: propertyNaming)
inertia4j.spring-boot-{3,4}/src/main/java/.../{Inertia,InertiaSpringAutoconfiguration}.java  (modify)
inertia4j.spring-boot-{3,4}/src/test/java/.../TypedPropsTest.java  (create)

inertia4j.ktor/src/main/kotlin/{InertiaKtorConfiguration,InertiaKtorRenderer}.kt  (modify)
inertia4j.ktor/src/test/kotlin/InertiaKtorTypedPropsTest.kt                     (create)

inertia4j.typescript/
  build.gradle.kts
  src/main/java/io/github/inertia4j/typescript/
    TypeScriptGenerator.java   public entry point
    GeneratorOptions.java      public record of options
    ErrorValueType.java        public enum
    GenerationResult.java      public record (content, warnings)
    GenerationException.java   public fatal error
    TsType.java                type AST + rendering
    TsDeclaration.java         TsInterface / TsEnum / TsProperty
    TsModel.java               whole-file model
    TypeScriptWriter.java      model -> text
    ClassScanner.java          classpath walk + role annotation filter
    Annotations.java           by-name annotation helpers
    TypeMapper.java            java.lang.reflect.Type -> TsType
    TypeModelBuilder.java      classes -> TsModel
  src/main/kotlin/io/github/inertia4j/typescript/KotlinNullability.kt
  src/test/java/io/github/inertia4j/typescript/...Test.java + fixtures/<case>/*.java
  src/test/kotlin/io/github/inertia4j/typescript/fixtures/kotlin/UserProps.kt
  src/test/resources/expected/sample.d.ts

inertia4j.typescript-gradle-plugin/
  build.gradle.kts
  src/main/java/io/github/inertia4j/typescript/gradle/
    InertiaTypesPlugin.java, InertiaTypesExtension.java,
    InertiaTypesTask.java (abstract base), GenerateInertiaTypesTask.java, CheckInertiaTypesTask.java
  src/test/java/io/github/inertia4j/typescript/gradle/InertiaTypesPluginTest.java

docs/typescript.md (create), README.md + adapter READMEs + docs/roadmap.md (modify)
```

---

### Task 1: Annotations module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `inertia4j.typescript-annotations/build.gradle.kts`
- Create: `inertia4j.typescript-annotations/src/main/java/io/github/inertia4j/annotations/InertiaPage.java`
- Create: `inertia4j.typescript-annotations/src/main/java/io/github/inertia4j/annotations/InertiaShared.java`
- Create: `inertia4j.typescript-annotations/src/main/java/io/github/inertia4j/annotations/InertiaForm.java`
- Create: `inertia4j.typescript-annotations/src/main/java/io/github/inertia4j/annotations/TypeScriptName.java`
- Test: `inertia4j.typescript-annotations/src/test/java/AnnotationsTest.java`

**Interfaces:**
- Produces: `@InertiaPage(String value)`, `@InertiaShared`, `@InertiaForm`, `@TypeScriptName(String value)` in package `io.github.inertia4j.annotations`, all `@Retention(RUNTIME) @Target(TYPE) @Documented`. Gradle project path `:inertia4j.typescript-annotations`.

- [ ] **Step 1: Register module and build file**

Append to `settings.gradle.kts`:

```kotlin
include("inertia4j.typescript-annotations")
```

Create `inertia4j.typescript-annotations/build.gradle.kts`:

```kotlin
plugins {
    id("inertia4j.publishing-conventions")
}

version = "1.0.0"

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J TypeScript Annotations"
        description = "Annotations describing Inertia4J props classes for TypeScript generation"
        inceptionYear = "2026"
    }
}
```

- [ ] **Step 2: Write the failing test**

`inertia4j.typescript-annotations/src/test/java/AnnotationsTest.java`:

```java
import io.github.inertia4j.annotations.InertiaForm;
import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.annotations.InertiaShared;
import io.github.inertia4j.annotations.TypeScriptName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationsTest {
    @InertiaPage("Users/Show")
    @TypeScriptName("UserPage")
    static class PageProps {}

    @InertiaShared
    static class SharedProps {}

    @InertiaForm
    static class FormProps {}

    @Test
    void annotations_areVisibleAtRuntime() {
        assertEquals("Users/Show", PageProps.class.getAnnotation(InertiaPage.class).value());
        assertEquals("UserPage", PageProps.class.getAnnotation(TypeScriptName.class).value());
        assertTrue(SharedProps.class.isAnnotationPresent(InertiaShared.class));
        assertTrue(FormProps.class.isAnnotationPresent(InertiaForm.class));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :inertia4j.typescript-annotations:test`
Expected: FAIL — compilation error `package io.github.inertia4j.annotations does not exist`.

- [ ] **Step 4: Write the annotations**

`InertiaPage.java`:

```java
package io.github.inertia4j.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class describing the props of an Inertia page component.
 * Instances can be passed to the adapters' {@code render(Object)} methods, and the TypeScript generator
 * emits an interface for the class, mapped to the component name in {@code InertiaPages}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InertiaPage {
    /**
     * Name of the client-side page component, e.g. {@code "Users/Show"}.
     *
     * @return component name.
     */
    String value();
}
```

`InertiaShared.java`:

```java
package io.github.inertia4j.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class describing (part of) the props shared with every Inertia page.
 * The TypeScript generator adds it to {@code InertiaConfig.sharedPageProps}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InertiaShared {
}
```

`InertiaForm.java`:

```java
package io.github.inertia4j.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class describing a request body sent by the frontend, e.g. with {@code useForm}.
 * The TypeScript generator emits an interface for it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InertiaForm {
}
```

`TypeScriptName.java`:

```java
package io.github.inertia4j.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the name of the TypeScript interface or type generated for a class.
 * Use it to resolve two classes sharing the same simple name.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TypeScriptName {
    /**
     * TypeScript type name.
     *
     * @return type name.
     */
    String value();
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :inertia4j.typescript-annotations:test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts inertia4j.typescript-annotations
git commit -m "Add TypeScript annotations module"
```

---

### Task 2: Generic `DeferredProp<T>` and `MergeProp<T>`

**Files:**
- Modify: `inertia4j.core/src/main/java/io/github/inertia4j/core/DeferredProp.java`
- Modify: `inertia4j.core/src/main/java/io/github/inertia4j/core/MergeProp.java`
- Modify: `inertia4j.core/src/main/java/io/github/inertia4j/core/InertiaProps.java`
- Modify: `inertia4j.core/src/main/java/io/github/inertia4j/core/PropsResolver.java`
- Modify: `inertia4j.spring-shared/src/main/java/io/github/inertia4j/springshared/AbstractInertia.java` (static `defer`/`merge`/`deepMerge`, ~lines 88-125)
- Test: `inertia4j.core/src/test/java/InertiaRendererTest.java`

**Interfaces:**
- Produces: `DeferredProp<T> extends MergeableProp<DeferredProp<T>>` with `T resolve()`; `MergeProp<T> extends MergeableProp<MergeProp<T>>` with `Object getValue()`; `InertiaProps.<T>defer(Supplier<? extends T>)`, `<T>defer(Supplier<? extends T>, String)`, `<T>merge(T)`, `<T>deepMerge(T)` returning the generic types. Same signatures on `AbstractInertia` statics.

- [ ] **Step 1: Write the failing test**

Add to `InertiaRendererTest` (default package; add imports `java.util.List` already present):

```java
    @Test
    void defer_andMerge_keepValueType() {
        DeferredProp<List<Integer>> deferred = InertiaProps.defer(() -> List.of(1));
        MergeProp<List<Integer>> merged = InertiaProps.merge(List.of(2));
        MergeProp<List<Integer>> deepMerged = InertiaProps.deepMerge(List.of(3));

        assertEquals(List.of(1), deferred.resolve());
        assertEquals(List.of(2), merged.getValue());
        assertEquals(List.of(3), deepMerged.getValue());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :inertia4j.core:test --tests InertiaRendererTest`
Expected: FAIL — compilation error `type DeferredProp does not take parameters`.

- [ ] **Step 3: Make the props generic**

`DeferredProp.java` — replace class body:

```java
/**
 * A prop that is left out of the initial page load and fetched by the client in a follow-up partial reload.
 * Deferred props sharing a group are fetched in the same request.
 * Create instances through {@link InertiaProps#defer(Supplier)} or {@link InertiaProps#defer(Supplier, String)}.
 *
 * @param <T> type of the resolved value.
 * @see <a href="https://inertiajs.com/deferred-props">Inertia deferred props</a>
 */
public class DeferredProp<T> extends MergeableProp<DeferredProp<T>> {
    /**
     * Name of the group deferred props belong to when none is specified.
     */
    public static final String DefaultGroup = "default";

    private final Supplier<? extends T> supplier;
    private final String group;

    DeferredProp(Supplier<? extends T> supplier, String group) {
        this.supplier = supplier;
        this.group = group;
    }

    /**
     * Gets the group this prop is fetched with.
     *
     * @return group name.
     */
    public String getGroup() {
        return group;
    }

    /**
     * Resolves the value of this prop.
     *
     * @return resolved value.
     */
    public T resolve() {
        return supplier.get();
    }
}
```

`MergeProp.java`:

```java
/**
 * A prop whose value is merged by the client with the value it already holds during partial reloads.
 * Create instances through {@link InertiaProps#merge(Object)} or {@link InertiaProps#deepMerge(Object)}.
 *
 * @param <T> type of the wrapped value.
 * @see <a href="https://inertiajs.com/merging-props">Inertia merging props</a>
 */
public class MergeProp<T> extends MergeableProp<MergeProp<T>> {
    private final Object value;

    MergeProp(Object value) {
        this.value = value;
    }

    /**
     * Gets the wrapped value. It may be a {@link java.util.function.Supplier}, resolved only when the prop is sent.
     *
     * @return the wrapped value.
     */
    public Object getValue() {
        return value;
    }
}
```

`InertiaProps.java` — change the four factory signatures (javadoc unchanged, add `@param <T> type of the value.`):

```java
    public static <T> DeferredProp<T> defer(Supplier<? extends T> supplier) {
        return defer(supplier, DeferredProp.DefaultGroup);
    }

    public static <T> DeferredProp<T> defer(Supplier<? extends T> supplier, String group) {
        return new DeferredProp<>(supplier, group);
    }

    public static <T> MergeProp<T> merge(T value) {
        return new MergeProp<T>(value).merge();
    }

    public static <T> MergeProp<T> deepMerge(T value) {
        return new MergeProp<T>(value).deepMerge();
    }
```

`PropsResolver.java` — replace raw casts:

```java
        if (!partial && value instanceof DeferredProp) {
            String group = ((DeferredProp<?>) value).getGroup();
```

```java
        if (value instanceof DeferredProp) {
            return resolveValue(((DeferredProp<?>) value).resolve());
        }

        if (value instanceof MergeProp) {
            return resolveValue(((MergeProp<?>) value).getValue());
        }
```

`AbstractInertia.java` — update the four statics to delegate with the same generic signatures:

```java
    public static <T> DeferredProp<T> defer(Supplier<? extends T> supplier) {
        return InertiaProps.defer(supplier);
    }

    public static <T> DeferredProp<T> defer(Supplier<? extends T> supplier, String group) {
        return InertiaProps.defer(supplier, group);
    }

    public static <T> MergeProp<T> merge(T value) {
        return InertiaProps.merge(value);
    }

    public static <T> MergeProp<T> deepMerge(T value) {
        return InertiaProps.deepMerge(value);
    }
```

(Keep each existing javadoc, adding `@param <T> type of the value.`)

- [ ] **Step 4: Run the whole build's tests**

Run: `./gradlew test`
Expected: PASS (all modules; Ktor and Spring tests compile unchanged).

- [ ] **Step 5: Commit**

```bash
git add inertia4j.core inertia4j.spring-shared
git commit -m "Make DeferredProp and MergeProp generic over their value type"
```

---

### Task 3: `PropertyNaming` and `PropertyIntrospector` in core

**Files:**
- Modify: `inertia4j.core/build.gradle.kts`
- Create: `inertia4j.core/src/main/java/io/github/inertia4j/core/PropertyNaming.java`
- Create: `inertia4j.core/src/main/java/io/github/inertia4j/core/Property.java`
- Create: `inertia4j.core/src/main/java/io/github/inertia4j/core/PropertyIntrospector.java`
- Test: `inertia4j.core/src/test/java/PropertyNamingTest.java`
- Test: `inertia4j.core/src/test/java/PropertyIntrospectorTest.java`

**Interfaces:**
- Produces:
  - `enum PropertyNaming { Camel, Snake; String apply(String propertyName) }`
  - `final class Property`: `String getName()` (serialized name), `String getJavaName()`, `String getAccessorName()` (getter / record accessor method name), `Type getGenericType()`, `List<Annotation> getAnnotations()` (declaration + type-use annotations of accessor and backing field), `boolean hasAnnotation(String simpleName)`, `Optional<Annotation> findAnnotation(String simpleName)`, `Object read(Object target)` (throws `InertiaException`).
  - `final class PropertyIntrospector`: `static List<Property> properties(Class<?> type, PropertyNaming naming)`.

- [ ] **Step 1: Core build — annotations dependency, Java 17 tests, Jackson annotations for tests**

Replace `dependencies` in `inertia4j.core/build.gradle.kts` and add toolchain overrides:

```kotlin
dependencies {
    api(project(":inertia4j.typescript-annotations"))
    implementation(project(":inertia4j.spi"))

    compileOnly(libs.jackson2.databind)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jackson2.databind)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<JavaCompile>("compileTestJava") {
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named<Test>("test") {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
```

- [ ] **Step 2: Write the failing tests**

`inertia4j.core/src/test/java/PropertyNamingTest.java`:

```java
import io.github.inertia4j.core.PropertyNaming;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertyNamingTest {
    @Test
    void camel_keepsName() {
        assertEquals("firstName", PropertyNaming.Camel.apply("firstName"));
    }

    @Test
    void snake_matchesJacksonSnakeCase() {
        assertEquals("first_name", PropertyNaming.Snake.apply("firstName"));
        assertEquals("user_id", PropertyNaming.Snake.apply("userID"));
        assertEquals("urlvalue", PropertyNaming.Snake.apply("URLValue"));
        assertEquals("name", PropertyNaming.Snake.apply("name"));
    }
}
```

`inertia4j.core/src/test/java/PropertyIntrospectorTest.java`:

```java
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.inertia4j.core.Property;
import io.github.inertia4j.core.PropertyIntrospector;
import io.github.inertia4j.core.PropertyNaming;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertyIntrospectorTest {
    private record Album(String title, int year, List<String> tags) {}

    private record Annotated(
        @JsonProperty("full_name") String name,
        @JsonIgnore String secret,
        @Nullable String nickname,
        String firstName
    ) {}

    public static class Base {
        public long getId() { return 1; }
    }

    public static class Child extends Base {
        private String name = "child";
        private boolean active = true;

        public String getName() { return name; }
        public boolean isActive() { return active; }
        public String getComputed() { return "computed"; }
        public static String getStatic() { return "static"; }
    }

    @Test
    void record_listsComponentsInDeclarationOrder() {
        assertEquals(List.of("title", "year", "tags"), names(Album.class, PropertyNaming.Camel));
    }

    @Test
    void record_exposesGenericTypeAndAccessor() {
        Property tags = PropertyIntrospector.properties(Album.class, PropertyNaming.Camel).get(2);

        assertTrue(tags.getGenericType() instanceof ParameterizedType);
        assertEquals("tags", tags.getAccessorName());
    }

    @Test
    void bean_listsSuperclassFieldsFirstThenGetterOnlyPropertiesByName() {
        assertEquals(List.of("name", "active", "computed", "id"), names(Child.class, PropertyNaming.Camel));
    }

    @Test
    void jsonProperty_renamesAndJsonIgnore_skips() {
        assertEquals(List.of("full_name", "nickname", "firstName"), names(Annotated.class, PropertyNaming.Camel));
    }

    @Test
    void snakeNaming_appliesOnlyToUnannotatedProperties() {
        assertEquals(List.of("full_name", "nickname", "first_name"), names(Annotated.class, PropertyNaming.Snake));
    }

    @Test
    void typeUseNullable_isCollected() {
        List<Property> properties = PropertyIntrospector.properties(Annotated.class, PropertyNaming.Camel);

        assertTrue(properties.get(1).hasAnnotation("Nullable"));
        assertFalse(properties.get(2).hasAnnotation("Nullable"));
    }

    @Test
    void read_invokesAccessorOfPrivateRecord() {
        Property title = PropertyIntrospector.properties(Album.class, PropertyNaming.Camel).get(0);

        assertEquals("Kind of Blue", title.read(new Album("Kind of Blue", 1959, List.of())));
    }

    private static List<String> names(Class<?> type, PropertyNaming naming) {
        return PropertyIntrospector.properties(type, naming).stream()
            .map(Property::getName)
            .collect(Collectors.toList());
    }
}
```

Note on `bean_listsSuperclassFieldsFirstThenGetterOnlyPropertiesByName`: `Base` has no field for `id`, so `id` is a getter-only property; getter-only properties come after field-backed ones, sorted by name (`computed`, `id`).

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :inertia4j.core:test --tests PropertyNamingTest --tests PropertyIntrospectorTest`
Expected: FAIL — compilation errors, `PropertyNaming` / `PropertyIntrospector` not found.

- [ ] **Step 4: Implement `PropertyNaming`**

```java
package io.github.inertia4j.core;

/**
 * Naming strategy applied to property names that have no explicit {@code @JsonProperty} name.
 * Must match the strategy configured on the JSON serializer.
 */
public enum PropertyNaming {
    /**
     * Keeps Java property names, e.g. {@code firstName}.
     */
    Camel,
    /**
     * Converts to snake case like Jackson's {@code SNAKE_CASE}, e.g. {@code first_name}.
     */
    Snake;

    /**
     * Applies this strategy to a Java property name.
     *
     * @param propertyName Java property name.
     * @return serialized property name.
     */
    public String apply(String propertyName) {
        if (this == Camel) {
            return propertyName;
        }

        return toSnakeCase(propertyName);
    }

    private static String toSnakeCase(String propertyName) {
        StringBuilder result = new StringBuilder(propertyName.length() + 4);
        boolean previousUpper = false;

        for (int i = 0; i < propertyName.length(); i++) {
            char c = propertyName.charAt(i);
            boolean upper = Character.isUpperCase(c);

            if (upper && !previousUpper && result.length() > 0 && result.charAt(result.length() - 1) != '_') {
                result.append('_');
            }

            result.append(Character.toLowerCase(c));
            previousUpper = upper;
        }

        return result.toString();
    }
}
```

- [ ] **Step 5: Implement `Property`**

```java
package io.github.inertia4j.core;

import io.github.inertia4j.spi.InertiaException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

/**
 * A serialized property of a props class, as discovered by {@link PropertyIntrospector}.
 */
public final class Property {
    private final String name;
    private final String javaName;
    private final Method accessor;
    private final List<Annotation> annotations;

    Property(String name, String javaName, Method accessor, List<Annotation> annotations) {
        this.name = name;
        this.javaName = javaName;
        this.accessor = accessor;
        this.annotations = List.copyOf(annotations);
    }

    /**
     * @return name of the property in the serialized JSON.
     */
    public String getName() {
        return name;
    }

    /**
     * @return Java (or Kotlin) name of the property.
     */
    public String getJavaName() {
        return javaName;
    }

    /**
     * @return name of the method reading the property.
     */
    public String getAccessorName() {
        return accessor.getName();
    }

    /**
     * @return generic type of the property.
     */
    public Type getGenericType() {
        return accessor.getGenericReturnType();
    }

    /**
     * @return declaration and type-use annotations of the accessor and its backing field.
     */
    public List<Annotation> getAnnotations() {
        return annotations;
    }

    /**
     * @param simpleName annotation simple name, e.g. {@code "Nullable"}.
     * @return whether an annotation with this simple name is present.
     */
    public boolean hasAnnotation(String simpleName) {
        return findAnnotation(simpleName).isPresent();
    }

    /**
     * @param simpleName annotation simple name, e.g. {@code "JsonInclude"}.
     * @return the first annotation with this simple name.
     */
    public Optional<Annotation> findAnnotation(String simpleName) {
        return annotations.stream()
            .filter(annotation -> annotation.annotationType().getSimpleName().equals(simpleName))
            .findFirst();
    }

    /**
     * Reads the property value.
     *
     * @param target object holding the property.
     * @return property value.
     * @throws InertiaException if the accessor cannot be invoked.
     */
    public Object read(Object target) {
        try {
            accessor.trySetAccessible();
            return accessor.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new InertiaException("Could not read property `" + javaName + "` of " + target.getClass().getName(), e);
        }
    }
}
```

- [ ] **Step 6: Implement `PropertyIntrospector`**

```java
package io.github.inertia4j.core;

import io.github.inertia4j.spi.InertiaException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lists the serialized properties of a props class: record components, or bean getters.
 * <p>
 * Follows Jackson's defaults: {@code @JsonProperty} renames, {@code @JsonIgnore} skips (Jackson 2 and 3 annotations
 * are matched by simple name), other names go through the {@link PropertyNaming} strategy.
 * Record components keep their declaration order; bean properties backed by a field come first in field order
 * (superclass fields first), followed by getter-only properties sorted by name.
 */
public final class PropertyIntrospector {
    private PropertyIntrospector() {}

    /**
     * @param type   props class.
     * @param naming naming strategy for properties without explicit name.
     * @return serialized properties, in order.
     */
    public static List<Property> properties(Class<?> type, PropertyNaming naming) {
        List<Property> properties = new ArrayList<>();

        for (Candidate candidate : isRecord(type) ? recordCandidates(type) : beanCandidates(type)) {
            List<Annotation> annotations = annotationsOf(candidate);
            if (hasAnnotation(annotations, "JsonIgnore")) {
                continue;
            }

            String name = explicitName(annotations).orElseGet(() -> naming.apply(candidate.javaName));
            properties.add(new Property(name, candidate.javaName, candidate.accessor, annotations));
        }

        return properties;
    }

    private static boolean isRecord(Class<?> type) {
        Class<?> superclass = type.getSuperclass();

        return superclass != null && superclass.getName().equals("java.lang.Record");
    }

    private static List<Candidate> recordCandidates(Class<?> type) {
        try {
            Object[] components = (Object[]) Class.class.getMethod("getRecordComponents").invoke(type);
            List<Candidate> candidates = new ArrayList<>();

            for (Object component : components) {
                String name = (String) component.getClass().getMethod("getName").invoke(component);
                Method accessor = (Method) component.getClass().getMethod("getAccessor").invoke(component);
                candidates.add(new Candidate(name, accessor, findField(type, name)));
            }

            return candidates;
        } catch (ReflectiveOperationException e) {
            throw new InertiaException("Could not read record components of " + type.getName(), e);
        }
    }

    private static List<Candidate> beanCandidates(Class<?> type) {
        Map<String, Method> getters = new LinkedHashMap<>();
        for (Method method : type.getMethods()) {
            String propertyName = getterPropertyName(method);
            if (propertyName != null) {
                getters.put(propertyName, method);
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Field field : fieldsSuperclassFirst(type)) {
            Method getter = getters.remove(field.getName());
            if (getter != null) {
                candidates.add(new Candidate(field.getName(), getter, field));
            }
        }

        getters.keySet().stream()
            .sorted()
            .forEach(name -> candidates.add(new Candidate(name, getters.get(name), null)));

        return candidates;
    }

    private static String getterPropertyName(Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            return null;
        }

        if (method.getParameterCount() != 0 || method.isBridge() || method.isSynthetic()) {
            return null;
        }

        if (method.getDeclaringClass() == Object.class) {
            return null;
        }

        String name = method.getName();
        Class<?> returnType = method.getReturnType();

        if (name.startsWith("get") && name.length() > 3 && returnType != void.class) {
            return decapitalize(name.substring(3));
        }

        if (name.startsWith("is") && name.length() > 2 && (returnType == boolean.class || returnType == Boolean.class)) {
            return decapitalize(name.substring(2));
        }

        return null;
    }

    private static String decapitalize(String name) {
        char[] chars = name.toCharArray();
        for (int i = 0; i < chars.length && Character.isUpperCase(chars[i]); i++) {
            chars[i] = Character.toLowerCase(chars[i]);
        }

        return new String(chars);
    }

    private static List<Field> fieldsSuperclassFirst(Class<?> type) {
        Deque<Class<?>> hierarchy = new ArrayDeque<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.push(current);
        }

        List<Field> fields = new ArrayList<>();
        for (Class<?> current : hierarchy) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }

        return fields;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass
            }
        }

        return null;
    }

    private static List<Annotation> annotationsOf(Candidate candidate) {
        List<Annotation> annotations = new ArrayList<>();
        Collections.addAll(annotations, candidate.accessor.getAnnotations());
        Collections.addAll(annotations, candidate.accessor.getAnnotatedReturnType().getAnnotations());

        if (candidate.field != null) {
            Collections.addAll(annotations, candidate.field.getAnnotations());
            Collections.addAll(annotations, candidate.field.getAnnotatedType().getAnnotations());
        }

        return annotations;
    }

    private static boolean hasAnnotation(List<Annotation> annotations, String simpleName) {
        return annotations.stream().anyMatch(annotation -> annotation.annotationType().getSimpleName().equals(simpleName));
    }

    private static Optional<String> explicitName(List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (!annotation.annotationType().getSimpleName().equals("JsonProperty")) {
                continue;
            }

            try {
                String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
                if (!value.isEmpty()) {
                    return Optional.of(value);
                }
            } catch (ReflectiveOperationException e) {
                throw new InertiaException("Could not read @JsonProperty value", e);
            }
        }

        return Optional.empty();
    }

    private static final class Candidate {
        final String javaName;
        final Method accessor;
        final Field field;

        Candidate(String javaName, Method accessor, Field field) {
            this.javaName = javaName;
            this.accessor = accessor;
            this.field = field;
        }
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :inertia4j.core:test`
Expected: PASS (new and existing tests).

- [ ] **Step 8: Commit**

```bash
git add inertia4j.core
git commit -m "Add property introspection and naming strategies to core"
```

---

### Task 4: `PropsExtractor` in core

**Files:**
- Create: `inertia4j.core/src/main/java/io/github/inertia4j/core/PropsExtractor.java`
- Test: `inertia4j.core/src/test/java/PropsExtractorTest.java`

**Interfaces:**
- Consumes: `PropertyIntrospector.properties(Class<?>, PropertyNaming)`, `Property.read(Object)`, `@InertiaPage`.
- Produces: `PropsExtractor.toMap(Object props, PropertyNaming naming): Map<String, Object>` (`LinkedHashMap`, nulls kept, `DeferredProp`/`MergeProp`/`Supplier` values untouched, Kotlin `Function0` values wrapped in a `Supplier`); `PropsExtractor.toMap(Object props)` (Camel); `PropsExtractor.componentName(Object pageProps): String` throwing `IllegalArgumentException` when `@InertiaPage` is missing.

- [ ] **Step 1: Write the failing test**

`inertia4j.core/src/test/java/PropsExtractorTest.java`:

```java
import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.core.DeferredProp;
import io.github.inertia4j.core.InertiaProps;
import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.core.PropsExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropsExtractorTest {
    @InertiaPage("Albums/Index")
    private record AlbumsIndex(List<String> titles, String firstName, DeferredProp<Integer> total, String note) {}

    private record NotAPage(String name) {}

    @Test
    void toMap_keepsDeclarationOrderAndNullValues() {
        Map<String, Object> props = PropsExtractor.toMap(new AlbumsIndex(List.of("A"), "Miles", null, null));

        assertEquals(List.of("titles", "firstName", "total", "note"), List.copyOf(props.keySet()));
        assertEquals(List.of("A"), props.get("titles"));
        assertNull(props.get("note"));
        assertTrue(props.containsKey("note"));
    }

    @Test
    void toMap_appliesNaming() {
        Map<String, Object> props = PropsExtractor.toMap(new AlbumsIndex(List.of(), "Miles", null, null), PropertyNaming.Snake);

        assertEquals("Miles", props.get("first_name"));
    }

    @Test
    void toMap_leavesInertiaPropsUntouched() {
        DeferredProp<Integer> total = InertiaProps.defer(() -> 3);

        Map<String, Object> props = PropsExtractor.toMap(new AlbumsIndex(List.of(), "Miles", total, null));

        assertSame(total, props.get("total"));
    }

    @Test
    void componentName_readsInertiaPage() {
        assertEquals("Albums/Index", PropsExtractor.componentName(new AlbumsIndex(List.of(), "", null, null)));
    }

    @Test
    void componentName_withoutInertiaPage_throws() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> PropsExtractor.componentName(new NotAPage("x"))
        );

        assertTrue(exception.getMessage().contains("NotAPage"));
        assertTrue(exception.getMessage().contains("@InertiaPage"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :inertia4j.core:test --tests PropsExtractorTest`
Expected: FAIL — `cannot find symbol: class PropsExtractor`.

- [ ] **Step 3: Implement `PropsExtractor`**

```java
package io.github.inertia4j.core;

import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.spi.InertiaException;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Converts typed props objects (records, beans, Kotlin classes) into the props map sent to the client.
 * Uses the same property rules as the TypeScript generator, so the JSON keys match the generated types.
 */
public final class PropsExtractor {
    private static final String KotlinFunction0 = "kotlin.jvm.functions.Function0";

    private PropsExtractor() {}

    /**
     * Converts a props object with {@link PropertyNaming#Camel} naming.
     *
     * @param props props object.
     * @return ordered props map.
     */
    public static Map<String, Object> toMap(Object props) {
        return toMap(props, PropertyNaming.Camel);
    }

    /**
     * Converts a props object. {@link DeferredProp}, {@link MergeProp} and {@link Supplier} values are kept as-is;
     * Kotlin {@code () -> T} values become lazy {@link Supplier}s.
     *
     * @param props  props object.
     * @param naming naming strategy for properties without explicit name.
     * @return ordered props map.
     */
    public static Map<String, Object> toMap(Object props, PropertyNaming naming) {
        Map<String, Object> map = new LinkedHashMap<>();

        for (Property property : PropertyIntrospector.properties(props.getClass(), naming)) {
            map.put(property.getName(), toLazyProp(property.read(props)));
        }

        return map;
    }

    /**
     * Reads the component name from the {@link InertiaPage} annotation of a page props object.
     *
     * @param pageProps page props object.
     * @return component name.
     * @throws IllegalArgumentException if the class is not annotated with {@link InertiaPage}.
     */
    public static String componentName(Object pageProps) {
        InertiaPage page = pageProps.getClass().getAnnotation(InertiaPage.class);
        if (page == null) {
            throw new IllegalArgumentException(
                pageProps.getClass().getName() + " is not annotated with @InertiaPage, pass the component name explicitly"
            );
        }

        return page.value();
    }

    private static Object toLazyProp(Object value) {
        if (value == null) {
            return null;
        }

        Class<?> function0 = findInterface(value.getClass(), KotlinFunction0);
        if (function0 == null) {
            return value;
        }

        return (Supplier<Object>) () -> invoke(function0, value);
    }

    private static Class<?> findInterface(Class<?> type, String interfaceName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> implemented : current.getInterfaces()) {
                if (implemented.getName().equals(interfaceName)) {
                    return implemented;
                }
            }
        }

        return null;
    }

    private static Object invoke(Class<?> function0, Object function) {
        try {
            Method invoke = function0.getMethod("invoke");
            return invoke.invoke(function);
        } catch (ReflectiveOperationException e) {
            throw new InertiaException("Could not evaluate lazy Kotlin prop", e);
        }
    }
}
```

(Kotlin `Function0` conversion is exercised by the Ktor test in Task 6.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :inertia4j.core:test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add inertia4j.core
git commit -m "Add PropsExtractor converting typed props objects to props maps"
```

---

### Task 5: Spring typed rendering, typed shared data and property naming

**Files:**
- Create: `inertia4j.spring-shared/src/main/java/io/github/inertia4j/springshared/TypedSharedDataProvider.java`
- Modify: `inertia4j.spring-shared/src/main/java/io/github/inertia4j/springshared/AbstractInertia.java`
- Modify: `inertia4j.spring-shared/src/main/java/io/github/inertia4j/springshared/InertiaConfigurationProperties.java`
- Modify: `inertia4j.spring-boot-3/src/main/java/io/github/inertia4j/springboot3/Inertia.java`
- Modify: `inertia4j.spring-boot-3/src/main/java/io/github/inertia4j/springboot3/InertiaSpringAutoconfiguration.java`
- Modify: `inertia4j.spring-boot-4/src/main/java/io/github/inertia4j/springboot4/Inertia.java`
- Modify: `inertia4j.spring-boot-4/src/main/java/io/github/inertia4j/springboot4/InertiaSpringAutoconfiguration.java`
- Test: `inertia4j.spring-boot-3/src/test/java/io/github/inertia4j/springboot3/TypedPropsTest.java`
- Test: `inertia4j.spring-boot-4/src/test/java/io/github/inertia4j/springboot4/TypedPropsTest.java`

**Interfaces:**
- Consumes: `PropsExtractor.toMap(Object, PropertyNaming)`, `PropsExtractor.componentName(Object)`, `PropertyNaming`.
- Produces:
  - `TypedSharedDataProvider extends SharedDataProvider` with abstract `Object shareTyped(HttpServletRequest)`.
  - `AbstractInertia.render(Object pageProps)`, `render(Object pageProps, InertiaSpringRendererOptions options)`.
  - `AbstractInertia` protected constructors gaining a trailing `PropertyNaming propertyNaming` parameter.
  - `InertiaConfigurationProperties.getPropertyNaming()`; property `inertia.property-naming`.
  - Boot 3/4 `Inertia` public constructor `(VersionProvider, PageObjectSerializer, TemplateRenderer, List<SharedDataProvider>, PropertyNaming)` and package-private `(VersionProvider, PageObjectSerializer, TemplateRenderer, Supplier<HttpServletRequest>, List<SharedDataProvider>, PropertyNaming)`.

- [ ] **Step 1: Write the failing Spring Boot 3 test**

`inertia4j.spring-boot-3/src/test/java/io/github/inertia4j/springboot3/TypedPropsTest.java`:

```java
package io.github.inertia4j.springboot3;

import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.annotations.InertiaShared;
import io.github.inertia4j.core.DefaultPageObjectSerializer;
import io.github.inertia4j.core.DeferredProp;
import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.springshared.InertiaConfigurationProperties;
import io.github.inertia4j.springshared.SharedDataProvider;
import io.github.inertia4j.springshared.TypedSharedDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TypedPropsTest {
    @InertiaPage("Records/Index")
    private record RecordsIndexProps(List<String> records, DeferredProp<List<Integer>> stats) {}

    @InertiaPage("Users/Show")
    private record UsersShowProps(String firstName) {}

    @InertiaShared
    private record AppShared(String appName) {}

    private record NotAPage(String name) {}

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/records");
        request.addHeader("X-Inertia", "true");
    }

    private Inertia inertia(List<SharedDataProvider> sharedDataProviders, PropertyNaming naming) {
        return new Inertia(
            () -> "1",
            new DefaultPageObjectSerializer(),
            page -> "",
            () -> request,
            sharedDataProviders,
            naming
        );
    }

    @Test
    void render_withPageProps_usesAnnotatedComponentAndProps() {
        ResponseEntity<String> response = inertia(List.of(), PropertyNaming.Camel)
            .render(new RecordsIndexProps(List.of("a"), Inertia.defer(() -> List.of(1))));

        assertEquals(
            "{\"component\":\"Records/Index\",\"props\":{\"records\":[\"a\"]},\"url\":\"/records\",\"version\":\"1\","
                + "\"encryptHistory\":false,\"clearHistory\":false,\"deferredProps\":{\"default\":[\"stats\"]}}",
            response.getBody()
        );
    }

    @Test
    void render_withPagePropsAndOptions_appliesOptions() {
        ResponseEntity<String> response = inertia(List.of(), PropertyNaming.Camel)
            .render(new UsersShowProps("Miles"), Inertia.Options.clearHistory());

        assertEquals(
            "{\"component\":\"Users/Show\",\"props\":{\"firstName\":\"Miles\"},\"url\":\"/records\",\"version\":\"1\","
                + "\"encryptHistory\":false,\"clearHistory\":true}",
            response.getBody()
        );
    }

    @Test
    void render_withPropsWithoutInertiaPage_throws() {
        Inertia inertia = inertia(List.of(), PropertyNaming.Camel);

        assertThrows(IllegalArgumentException.class, () -> inertia.render(new NotAPage("x")));
    }

    @Test
    void render_withTypedSharedDataProvider_mergesSharedProps() {
        TypedSharedDataProvider appShared = request -> new AppShared("Inertia4J");

        ResponseEntity<String> response = inertia(List.of(appShared), PropertyNaming.Camel)
            .render(new UsersShowProps("Miles"));

        assertEquals(
            "{\"component\":\"Users/Show\",\"props\":{\"appName\":\"Inertia4J\",\"firstName\":\"Miles\"},\"url\":\"/records\","
                + "\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false}",
            response.getBody()
        );
    }

    @Test
    void render_withSnakeNaming_renamesPropsAndSharedProps() {
        TypedSharedDataProvider appShared = request -> new AppShared("Inertia4J");

        ResponseEntity<String> response = inertia(List.of(appShared), PropertyNaming.Snake)
            .render(new UsersShowProps("Miles"));

        assertEquals(
            "{\"component\":\"Users/Show\",\"props\":{\"app_name\":\"Inertia4J\",\"first_name\":\"Miles\"},\"url\":\"/records\","
                + "\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false}",
            response.getBody()
        );
    }

    @Test
    void propertyNaming_bindsLowerCaseValue() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("inertia.property-naming", "snake")));

        InertiaConfigurationProperties properties = binder.bind("inertia", InertiaConfigurationProperties.class).get();

        assertEquals(PropertyNaming.Snake, properties.getPropertyNaming());
    }

    @Test
    void propertyNaming_defaultsToCamel() {
        assertEquals(PropertyNaming.Camel, new InertiaConfigurationProperties().getPropertyNaming());
    }
}
```

Note: the template renderer lambda (`page -> ""`) is never called: these tests send `X-Inertia: true`, so the JSON page object is returned instead of HTML.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :inertia4j.spring-boot-3:test --tests '*TypedPropsTest'`
Expected: FAIL — compilation errors (`TypedSharedDataProvider` missing, no matching `Inertia` constructor, no `render(Object)`).

- [ ] **Step 3: Create `TypedSharedDataProvider`**

```java
package io.github.inertia4j.springshared;

import io.github.inertia4j.core.PropsExtractor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * A {@link SharedDataProvider} returning a typed object, typically a class annotated with
 * {@link io.github.inertia4j.annotations.InertiaShared}, instead of a map.
 * Its properties are converted with the configured {@code inertia.property-naming}.
 */
@FunctionalInterface
public interface TypedSharedDataProvider extends SharedDataProvider {
    /**
     * Returns the object whose properties are shared with the response to the given request.
     *
     * @param request the current request.
     * @return shared props object.
     */
    Object shareTyped(HttpServletRequest request);

    @Override
    default Map<String, Object> share(HttpServletRequest request) {
        return PropsExtractor.toMap(shareTyped(request));
    }
}
```

- [ ] **Step 4: Add `propertyNaming` to `InertiaConfigurationProperties`**

Replace fields and constructors (keep class javadoc, add `inertia.property-naming=snake` to the example):

```java
    private static final String defaultTemplatePath = "templates/app.html";
    private static final boolean defaultEncryptHistory = false;

    final String templatePath;
    final boolean encryptHistory;
    /**
     * Naming strategy used when converting typed props objects.
     * Corresponds to the `inertia.property-naming` property (`camel` or `snake`).
     */
    final PropertyNaming propertyNaming;

    /**
     * Constructor used by Spring Boot for property binding.
     * @param templatePath Value of `inertia.template-path`.
     * @param encryptHistory Value of `inertia.encrypt-history`.
     * @param propertyNaming Value of `inertia.property-naming`, defaults to camel case.
     */
    @ConstructorBinding
    public InertiaConfigurationProperties(String templatePath, boolean encryptHistory, PropertyNaming propertyNaming) {
        this.templatePath = templatePath;
        this.encryptHistory = encryptHistory;
        this.propertyNaming = propertyNaming == null ? PropertyNaming.Camel : propertyNaming;
    }

    /**
     * Constructor using default `propertyNaming`.
     * @param templatePath Value of `inertia.template-path`.
     * @param encryptHistory Value of `inertia.encrypt-history`.
     */
    public InertiaConfigurationProperties(String templatePath, boolean encryptHistory) {
        this(templatePath, encryptHistory, PropertyNaming.Camel);
    }

    // keep the three existing convenience constructors unchanged (they delegate to the 2-arg one)

    /**
     * @return naming strategy used when converting typed props objects.
     */
    public PropertyNaming getPropertyNaming() {
        return propertyNaming;
    }
```

Add import `io.github.inertia4j.core.PropertyNaming;` (keep the existing field javadocs on `templatePath` / `encryptHistory`).

- [ ] **Step 5: Update `AbstractInertia`**

Add field and imports (`io.github.inertia4j.core.PropertyNaming`, `io.github.inertia4j.core.PropsExtractor`):

```java
    private final PropertyNaming propertyNaming;
```

Replace the constructors with:

```java
    /**
     * Internal constructor used in tests.
     */
    protected AbstractInertia(
        AbstractInertiaSpringRenderer renderer,
        Supplier<HttpServletRequest> requestSupplier,
        List<SharedDataProvider> sharedDataProviders,
        PropertyNaming propertyNaming
    ) {
        this.renderer = renderer;
        this.requestSupplier = requestSupplier;
        this.sharedDataProviders = sharedDataProviders;
        this.propertyNaming = propertyNaming;
    }

    /**
     * Internal constructor used in tests.
     */
    protected AbstractInertia(
        AbstractInertiaSpringRenderer renderer,
        Supplier<HttpServletRequest> requestSupplier,
        List<SharedDataProvider> sharedDataProviders
    ) {
        this(renderer, requestSupplier, sharedDataProviders, PropertyNaming.Camel);
    }

    /**
     * Internal constructor used in tests.
     */
    protected AbstractInertia(
        AbstractInertiaSpringRenderer renderer,
        Supplier<HttpServletRequest> requestSupplier
    ) {
        this(renderer, requestSupplier, List.of());
    }

    /**
     * Constructs the Inertia bean with required dependencies.
     *
     * @param renderer            The Spring-specific renderer to use.
     * @param sharedDataProviders Providers of the data shared with every response.
     * @param propertyNaming      Naming strategy used when converting typed props objects.
     */
    protected AbstractInertia(
        AbstractInertiaSpringRenderer renderer,
        List<SharedDataProvider> sharedDataProviders,
        PropertyNaming propertyNaming
    ) {
        this(renderer, AbstractInertia::getCurrentRequest, sharedDataProviders, propertyNaming);
    }

    /**
     * Constructs the Inertia bean with required dependencies.
     *
     * @param renderer            The Spring-specific renderer to use.
     * @param sharedDataProviders Providers of the data shared with every response.
     */
    protected AbstractInertia(AbstractInertiaSpringRenderer renderer, List<SharedDataProvider> sharedDataProviders) {
        this(renderer, sharedDataProviders, PropertyNaming.Camel);
    }

    /**
     * Constructs the Inertia bean with required dependencies.
     *
     * @param renderer The Spring-specific renderer to use.
     */
    protected AbstractInertia(AbstractInertiaSpringRenderer renderer) {
        this(renderer, List.of());
    }
```

Add the typed render methods right after `render(String component)`:

```java
    /**
     * Renders the page described by a props object annotated with
     * {@link io.github.inertia4j.annotations.InertiaPage}, using its component name and properties.
     *
     * @param pageProps page props object.
     * @return A Spring {@link ResponseEntity} containing the Inertia response.
     * @throws IllegalArgumentException if the class is not annotated with {@code @InertiaPage}.
     */
    public ResponseEntity<String> render(Object pageProps) {
        return render(pageProps, defaultOptions);
    }

    /**
     * Renders the page described by a props object annotated with
     * {@link io.github.inertia4j.annotations.InertiaPage}, with specific rendering options.
     *
     * @param pageProps page props object.
     * @param options   Specific rendering options (e.g., history flags).
     * @return A Spring {@link ResponseEntity} containing the Inertia response.
     * @throws IllegalArgumentException if the class is not annotated with {@code @InertiaPage}.
     */
    public ResponseEntity<String> render(Object pageProps, InertiaSpringRendererOptions options) {
        return render(
            PropsExtractor.componentName(pageProps),
            PropsExtractor.toMap(pageProps, propertyNaming),
            options
        );
    }
```

In `withSharedProps`, replace the provider loop:

```java
        sharedDataProviders.forEach(provider -> allProps.putAll(sharedProps(provider, request)));
```

and add:

```java
    private Map<String, Object> sharedProps(SharedDataProvider provider, HttpServletRequest request) {
        if (provider instanceof TypedSharedDataProvider typedProvider) {
            return PropsExtractor.toMap(typedProvider.shareTyped(request), propertyNaming);
        }

        return provider.share(request);
    }
```

- [ ] **Step 6: Update Boot 3 `Inertia` and autoconfiguration**

`inertia4j.spring-boot-3/.../Inertia.java` — replace constructors with:

```java
    Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer,
        Supplier<HttpServletRequest> requestSupplier
    ) {
        this(versionProvider, pageObjectSerializer, templateRenderer, requestSupplier, List.of());
    }

    Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer,
        Supplier<HttpServletRequest> requestSupplier,
        List<SharedDataProvider> sharedDataProviders
    ) {
        this(versionProvider, pageObjectSerializer, templateRenderer, requestSupplier, sharedDataProviders, PropertyNaming.Camel);
    }

    Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer,
        Supplier<HttpServletRequest> requestSupplier,
        List<SharedDataProvider> sharedDataProviders,
        PropertyNaming propertyNaming
    ) {
        super(
            new InertiaSpringRenderer(pageObjectSerializer, versionProvider, templateRenderer),
            requestSupplier,
            sharedDataProviders,
            propertyNaming
        );
    }

    public Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer
    ) {
        this(versionProvider, pageObjectSerializer, templateRenderer, List.of());
    }

    public Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer,
        List<SharedDataProvider> sharedDataProviders
    ) {
        this(versionProvider, pageObjectSerializer, templateRenderer, sharedDataProviders, PropertyNaming.Camel);
    }

    public Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer,
        List<SharedDataProvider> sharedDataProviders,
        PropertyNaming propertyNaming
    ) {
        super(
            new InertiaSpringRenderer(pageObjectSerializer, versionProvider, templateRenderer),
            sharedDataProviders,
            propertyNaming
        );
    }
```

Add import `io.github.inertia4j.core.PropertyNaming;`.

`InertiaSpringAutoconfiguration.java` (Boot 3) — pass the naming:

```java
        return new Inertia(
            versionProvider,
            pageObjectSerializer,
            templateRenderer,
            sharedDataProviders.orderedStream().collect(Collectors.toList()),
            properties.getPropertyNaming()
        );
```

- [ ] **Step 7: Run Boot 3 tests**

Run: `./gradlew :inertia4j.spring-boot-3:test`
Expected: PASS (new `TypedPropsTest` and existing tests).

- [ ] **Step 8: Mirror for Boot 4**

Apply the exact Step 6 changes to `inertia4j.spring-boot-4/src/main/java/io/github/inertia4j/springboot4/Inertia.java` and `.../springboot4/InertiaSpringAutoconfiguration.java` (same constructor bodies; package `io.github.inertia4j.springboot4`).

Create `inertia4j.spring-boot-4/src/test/java/io/github/inertia4j/springboot4/TypedPropsTest.java` with the exact content of the Boot 3 test from Step 1, changing only the first line to `package io.github.inertia4j.springboot4;`.

- [ ] **Step 9: Run all Spring tests**

Run: `./gradlew :inertia4j.spring-boot-3:test :inertia4j.spring-boot-4:test`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add inertia4j.spring-shared inertia4j.spring-boot-3 inertia4j.spring-boot-4
git commit -m "Render typed page props and typed shared data in Spring adapters"
```

---

### Task 6: Ktor typed rendering, typed shared data and property naming

**Files:**
- Modify: `inertia4j.ktor/src/main/kotlin/InertiaKtorConfiguration.kt`
- Modify: `inertia4j.ktor/src/main/kotlin/InertiaKtorRenderer.kt`
- Test: `inertia4j.ktor/src/test/kotlin/InertiaKtorTypedPropsTest.kt`

**Interfaces:**
- Consumes: `PropsExtractor.toMap(Object, PropertyNaming)`, `PropsExtractor.componentName(Object)`, `PropertyNaming`.
- Produces: `InertiaKtorConfiguration.propertyNaming: PropertyNaming` (default `Camel`), `InertiaKtorConfiguration.shareTyped(provider: suspend (ApplicationCall) -> Any)`, `Renderer.render(pageProps: Any, url, encryptHistory, clearHistory)`.

- [ ] **Step 1: Write the failing test**

`inertia4j.ktor/src/test/kotlin/InertiaKtorTypedPropsTest.kt`:

```kotlin
package io.github.inertia4j.ktor

import io.github.inertia4j.annotations.InertiaPage
import io.github.inertia4j.annotations.InertiaShared
import io.github.inertia4j.core.PropertyNaming
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@InertiaPage("Records/Index")
data class RecordsIndexProps(val records: List<String>, val total: () -> Int)

@InertiaPage("Users/Show")
data class UsersShowProps(val firstName: String)

@InertiaShared
data class AppShared(val appName: String)

class InertiaKtorTypedPropsTest {
    private fun testApp(
        configure: InertiaKtorConfiguration.() -> Unit = {},
        block: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        application {
            install(Inertia) {
                versionProvider = { "1" }
                configure()
            }
        }
        block()
    }

    @Test
    fun `render typed page props uses annotated component and evaluates lazy props`() = testApp {
        routing {
            get("/") {
                inertia.render(RecordsIndexProps(listOf("a")) { 2 })
            }
        }

        val response = client.get("/") { header("X-Inertia", "true") }

        val expectedBody = """{"component":"Records/Index","props":{"records":["a"],"total":2},"url":"/","version":"1","encryptHistory":false,"clearHistory":false}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `render with component name only still renders`() = testApp {
        routing {
            get("/") {
                inertia.render("SampleComponent")
            }
        }

        val response = client.get("/") { header("X-Inertia", "true") }

        val expectedBody = """{"component":"SampleComponent","props":{},"url":"/","version":"1","encryptHistory":false,"clearHistory":false}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `shareTyped merges typed shared props`() = testApp({ shareTyped { AppShared("Inertia4J") } }) {
        routing {
            get("/") {
                inertia.render(UsersShowProps("Miles"))
            }
        }

        val response = client.get("/") { header("X-Inertia", "true") }

        val expectedBody = """{"component":"Users/Show","props":{"appName":"Inertia4J","firstName":"Miles"},"url":"/","version":"1","encryptHistory":false,"clearHistory":false}"""
        assertEquals(expectedBody, response.bodyAsText())
    }

    @Test
    fun `snake property naming renames typed props`() = testApp({
        propertyNaming = PropertyNaming.Snake
        shareTyped { AppShared("Inertia4J") }
    }) {
        routing {
            get("/") {
                inertia.render(UsersShowProps("Miles"))
            }
        }

        val response = client.get("/") { header("X-Inertia", "true") }

        val expectedBody = """{"component":"Users/Show","props":{"app_name":"Inertia4J","first_name":"Miles"},"url":"/","version":"1","encryptHistory":false,"clearHistory":false}"""
        assertEquals(expectedBody, response.bodyAsText())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :inertia4j.ktor:test --tests '*InertiaKtorTypedPropsTest'`
Expected: FAIL — `Unresolved reference: shareTyped` / `propertyNaming`.

- [ ] **Step 3: Update `InertiaKtorConfiguration`**

Add imports `io.github.inertia4j.core.PropertyNaming`, `io.github.inertia4j.core.PropsExtractor`, and after `encryptHistory`:

```kotlin
    /**
     * Naming strategy used when converting typed props objects. Defaults to [PropertyNaming.Camel].
     * Must match the naming strategy of the configured [serializer].
     */
    var propertyNaming: PropertyNaming = PropertyNaming.Camel
```

After `share`:

```kotlin
    /**
     * Registers a provider of a typed object, typically a class annotated with
     * [io.github.inertia4j.annotations.InertiaShared], whose properties are shared with every Inertia response.
     *
     * @param provider returns the shared props object for the given call.
     */
    fun shareTyped(provider: suspend (ApplicationCall) -> Any) {
        sharedDataProviders.add { call -> PropsExtractor.toMap(provider(call), propertyNaming) }
    }
```

- [ ] **Step 4: Add the typed `render` to `InertiaKtorRenderer.Renderer`**

Add import `io.github.inertia4j.core.PropsExtractor`, and after the existing `render`:

```kotlin
        /**
         * Renders the page described by a props object annotated with [io.github.inertia4j.annotations.InertiaPage],
         * using its component name and properties.
         *
         * @param pageProps page props object.
         * @param url The URL to be included in the page object (defaults to the current request URI).
         * @param encryptHistory Whether to encrypt the browser history state for this response (defaults to configuration setting).
         * @param clearHistory Whether to clear the browser history state for this response (defaults to false).
         * @throws IllegalArgumentException if the class is not annotated with `@InertiaPage`.
         */
        suspend fun render(
            pageProps: Any,
            url: String = request.url,
            encryptHistory: Boolean = configuration.encryptHistory,
            clearHistory: Boolean = false
        ) {
            val props = PropsExtractor.toMap(pageProps, configuration.propertyNaming)

            render(
                PropsExtractor.componentName(pageProps),
                *props.toList().toTypedArray(),
                url = url,
                encryptHistory = encryptHistory,
                clearHistory = clearHistory
            )
        }
```

- [ ] **Step 5: Run Ktor tests**

Run: `./gradlew :inertia4j.ktor:test`
Expected: PASS (all tests, including `render with component name only still renders`). If that test fails because Kotlin picked `render(pageProps: Any)` for a `String` argument, STOP and report — do not rename the API silently.

- [ ] **Step 6: Commit**

```bash
git add inertia4j.ktor
git commit -m "Render typed page props and typed shared data in Ktor adapter"
```

---

### Task 7: Generator module, type model and writer

**Files:**
- Modify: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`
- Create: `inertia4j.typescript/build.gradle.kts`
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/ErrorValueType.java`
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/TsType.java`
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/TsDeclaration.java`
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/TsModel.java`
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/TypeScriptWriter.java`
- Test: `inertia4j.typescript/src/test/java/io/github/inertia4j/typescript/TypeScriptWriterTest.java`

**Interfaces:**
- Produces (all package `io.github.inertia4j.typescript`):
  - `public enum ErrorValueType { String, StringArray; java.lang.String typeScript() }`
  - `sealed interface TsType` with `String render()` and constants `TsType.StringKeyword`, `NumberKeyword`, `BooleanKeyword`, `UnknownKeyword`; records `TsType.Keyword(String keyword)`, `TsType.Array(TsType element)`, `TsType.StringMap(TsType value)`, `TsType.NumberMap(TsType value)`, `TsType.EnumMap(TsType key, TsType value)`, `TsType.Nullable(TsType inner)` (+ `static TsType nullable(TsType)` that never double-wraps), `TsType.Reference(String name, List<TsType> arguments)`, `TsType.Variable(String name)`.
  - `sealed interface TsDeclaration { String name(); }` with records `TsDeclaration.Interface(String name, List<String> typeParameters, List<Property> properties)`, `TsDeclaration.Enum(String name, List<String> values)`, `TsDeclaration.Property(String name, TsType type, boolean optional)`.
  - `record TsModel(List<TsDeclaration> declarations, SortedMap<String, String> pages, List<String> sharedTypes, ErrorValueType errorValueType)`.
  - `final class TypeScriptWriter { static String write(TsModel model) }`.

- [ ] **Step 1: Build setup**

Append to `settings.gradle.kts`:

```kotlin
include("inertia4j.typescript")
```

Replace root `build.gradle.kts` with:

```kotlin
plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
}
```

Add to `[libraries]` in `gradle/libs.versions.toml`:

```toml
kotlin-metadata-jvm = { module = "org.jetbrains.kotlin:kotlin-metadata-jvm", version.ref = "kotlin" }
```

Create `inertia4j.typescript/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    id("inertia4j.publishing-conventions")
}

version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    api(project(":inertia4j.core"))

    implementation(libs.kotlin.metadata.jvm)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jackson2.databind)
    testImplementation(libs.jspecify)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    val testRuntimeClasspath = sourceSets["test"].runtimeClasspath
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dinertia4j.test.classpath=${testRuntimeClasspath.asPath}")
    })
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J TypeScript"
        description = "Generates TypeScript types for Inertia4J props classes"
        inceptionYear = "2026"
    }
}
```

- [ ] **Step 2: Write the failing writer test**

`inertia4j.typescript/src/test/java/io/github/inertia4j/typescript/TypeScriptWriterTest.java`:

```java
package io.github.inertia4j.typescript;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeScriptWriterTest {
    @Test
    void write_rendersDeclarationsPagesAndConfig() {
        TsType.Reference status = new TsType.Reference("Status", List.of());
        TsType.Reference stat = new TsType.Reference("Stat", List.of());

        TsModel model = new TsModel(
            List.of(
                new TsDeclaration.Interface("Stat", List.of(), List.of(
                    new TsDeclaration.Property("label", TsType.StringKeyword, false),
                    new TsDeclaration.Property("value", TsType.NumberKeyword, false)
                )),
                new TsDeclaration.Enum("Status", List.of("Active", "It's archived")),
                new TsDeclaration.Interface("Page", List.of("T"), List.of(
                    new TsDeclaration.Property("items", new TsType.Array(new TsType.Variable("T")), false)
                )),
                new TsDeclaration.Interface("IndexProps", List.of(), List.of(
                    new TsDeclaration.Property("stats", new TsType.Array(stat), true),
                    new TsDeclaration.Property("nickname", TsType.nullable(TsType.StringKeyword), false),
                    new TsDeclaration.Property("tags", new TsType.Array(TsType.nullable(TsType.StringKeyword)), false),
                    new TsDeclaration.Property("counts", new TsType.StringMap(TsType.NumberKeyword), false),
                    new TsDeclaration.Property("byId", new TsType.NumberMap(TsType.StringKeyword), false),
                    new TsDeclaration.Property("byStatus", new TsType.EnumMap(status, TsType.NumberKeyword), false),
                    new TsDeclaration.Property("page", new TsType.Reference("Page", List.of(stat)), false),
                    new TsDeclaration.Property("first-name", TsType.StringKeyword, false)
                )),
                new TsDeclaration.Interface("Empty", List.of(), List.of())
            ),
            new TreeMap<>(java.util.Map.of("Stats/Index", "IndexProps")),
            List.of("AppShared", "AuthShared"),
            ErrorValueType.StringArray
        );

        String expected = """
            // Generated by Inertia4J. Do not edit.
            import '@inertiajs/core'

            export interface Empty {}

            export interface IndexProps {
              stats?: Stat[]
              nickname: string | null
              tags: (string | null)[]
              counts: { [key: string]: number }
              byId: { [key: number]: string }
              byStatus: { [key in Status]?: number }
              page: Page<Stat>
              'first-name': string
            }

            export interface Page<T> {
              items: T[]
            }

            export interface Stat {
              label: string
              value: number
            }

            export type Status = 'Active' | 'It\\'s archived'

            export interface InertiaPages {
              'Stats/Index': IndexProps
            }

            export type PageProps<C extends keyof InertiaPages> = InertiaPages[C]

            declare module '@inertiajs/core' {
              export interface InertiaConfig {
                sharedPageProps: AppShared & AuthShared
                errorValueType: string[]
              }
            }
            """;

        assertEquals(expected, TypeScriptWriter.write(model));
    }

    @Test
    void write_withoutSharedTypesOrPages_omitsSharedPageProps() {
        TsModel model = new TsModel(List.of(), new TreeMap<>(), List.of(), ErrorValueType.String);

        String expected = """
            // Generated by Inertia4J. Do not edit.
            import '@inertiajs/core'

            export interface InertiaPages {}

            export type PageProps<C extends keyof InertiaPages> = InertiaPages[C]

            declare module '@inertiajs/core' {
              export interface InertiaConfig {
                errorValueType: string
              }
            }
            """;

        assertEquals(expected, TypeScriptWriter.write(model));
    }

    @Test
    void write_withEmptyEnum_rendersNever() {
        TsModel model = new TsModel(List.of(new TsDeclaration.Enum("Nothing", List.of())), new TreeMap<>(), List.of(), ErrorValueType.String);

        assertEquals(true, TypeScriptWriter.write(model).contains("export type Nothing = never\n"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :inertia4j.typescript:test --tests '*TypeScriptWriterTest'`
Expected: FAIL — compilation errors, types not found.

- [ ] **Step 4: Implement the model**

`ErrorValueType.java`:

```java
package io.github.inertia4j.typescript;

/**
 * Type of validation error values, emitted as {@code InertiaConfig.errorValueType}.
 */
public enum ErrorValueType {
    /**
     * One message per field: {@code string}.
     */
    String("string"),
    /**
     * Several messages per field: {@code string[]}.
     */
    StringArray("string[]");

    private final java.lang.String typeScript;

    ErrorValueType(java.lang.String typeScript) {
        this.typeScript = typeScript;
    }

    /**
     * @return TypeScript type.
     */
    public java.lang.String typeScript() {
        return typeScript;
    }
}
```

`TsType.java`:

```java
package io.github.inertia4j.typescript;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A TypeScript type expression.
 */
sealed interface TsType {
    TsType StringKeyword = new Keyword("string");
    TsType NumberKeyword = new Keyword("number");
    TsType BooleanKeyword = new Keyword("boolean");
    TsType UnknownKeyword = new Keyword("unknown");

    String render();

    static TsType nullable(TsType type) {
        if (type instanceof Nullable || type == UnknownKeyword) {
            return type;
        }

        return new Nullable(type);
    }

    record Keyword(String keyword) implements TsType {
        public String render() {
            return keyword;
        }
    }

    record Array(TsType element) implements TsType {
        public String render() {
            if (element instanceof Nullable) {
                return "(" + element.render() + ")[]";
            }

            return element.render() + "[]";
        }
    }

    record StringMap(TsType value) implements TsType {
        public String render() {
            return "{ [key: string]: " + value.render() + " }";
        }
    }

    record NumberMap(TsType value) implements TsType {
        public String render() {
            return "{ [key: number]: " + value.render() + " }";
        }
    }

    record EnumMap(TsType key, TsType value) implements TsType {
        public String render() {
            return "{ [key in " + key.render() + "]?: " + value.render() + " }";
        }
    }

    record Nullable(TsType inner) implements TsType {
        public String render() {
            return inner.render() + " | null";
        }
    }

    record Reference(String name, List<TsType> arguments) implements TsType {
        public String render() {
            if (arguments.isEmpty()) {
                return name;
            }

            return name + "<" + arguments.stream().map(TsType::render).collect(Collectors.joining(", ")) + ">";
        }
    }

    record Variable(String name) implements TsType {
        public String render() {
            return name;
        }
    }
}
```

`TsDeclaration.java`:

```java
package io.github.inertia4j.typescript;

import java.util.List;

/**
 * A top-level exported TypeScript declaration.
 */
sealed interface TsDeclaration {
    String name();

    record Interface(String name, List<String> typeParameters, List<Property> properties) implements TsDeclaration {}

    record Enum(String name, List<String> values) implements TsDeclaration {}

    record Property(String name, TsType type, boolean optional) {}
}
```

`TsModel.java`:

```java
package io.github.inertia4j.typescript;

import java.util.List;
import java.util.SortedMap;

/**
 * Everything written to the generated file.
 *
 * @param declarations   exported interfaces and types.
 * @param pages          component name to page props type name.
 * @param sharedTypes    shared props type names, sorted.
 * @param errorValueType type of validation error values.
 */
record TsModel(
    List<TsDeclaration> declarations,
    SortedMap<String, String> pages,
    List<String> sharedTypes,
    ErrorValueType errorValueType
) {}
```

- [ ] **Step 5: Implement `TypeScriptWriter`**

```java
package io.github.inertia4j.typescript;

import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Renders a {@link TsModel} as the content of a {@code .d.ts} file.
 */
final class TypeScriptWriter {
    private static final Pattern Identifier = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    private TypeScriptWriter() {}

    static String write(TsModel model) {
        StringBuilder out = new StringBuilder();
        out.append("// Generated by Inertia4J. Do not edit.\n");
        out.append("import '@inertiajs/core'\n");

        model.declarations().stream()
            .sorted(Comparator.comparing(TsDeclaration::name))
            .forEach(declaration -> {
                out.append('\n');
                writeDeclaration(out, declaration);
            });

        out.append('\n');
        writePages(out, model);
        out.append("\nexport type PageProps<C extends keyof InertiaPages> = InertiaPages[C]\n");
        out.append('\n');
        writeConfig(out, model);

        return out.toString();
    }

    private static void writeDeclaration(StringBuilder out, TsDeclaration declaration) {
        if (declaration instanceof TsDeclaration.Enum enumDeclaration) {
            String values = enumDeclaration.values().isEmpty()
                ? "never"
                : enumDeclaration.values().stream().map(TypeScriptWriter::quote).collect(Collectors.joining(" | "));
            out.append("export type ").append(enumDeclaration.name()).append(" = ").append(values).append('\n');
            return;
        }

        TsDeclaration.Interface interfaceDeclaration = (TsDeclaration.Interface) declaration;
        out.append("export interface ").append(interfaceDeclaration.name());

        if (!interfaceDeclaration.typeParameters().isEmpty()) {
            out.append('<').append(String.join(", ", interfaceDeclaration.typeParameters())).append('>');
        }

        if (interfaceDeclaration.properties().isEmpty()) {
            out.append(" {}\n");
            return;
        }

        out.append(" {\n");
        for (TsDeclaration.Property property : interfaceDeclaration.properties()) {
            out.append("  ")
                .append(propertyName(property.name()))
                .append(property.optional() ? "?: " : ": ")
                .append(property.type().render())
                .append('\n');
        }
        out.append("}\n");
    }

    private static void writePages(StringBuilder out, TsModel model) {
        if (model.pages().isEmpty()) {
            out.append("export interface InertiaPages {}\n");
            return;
        }

        out.append("export interface InertiaPages {\n");
        model.pages().forEach((component, type) ->
            out.append("  ").append(quote(component)).append(": ").append(type).append('\n')
        );
        out.append("}\n");
    }

    private static void writeConfig(StringBuilder out, TsModel model) {
        out.append("declare module '@inertiajs/core' {\n");
        out.append("  export interface InertiaConfig {\n");

        if (!model.sharedTypes().isEmpty()) {
            out.append("    sharedPageProps: ").append(String.join(" & ", model.sharedTypes())).append('\n');
        }

        out.append("    errorValueType: ").append(model.errorValueType().typeScript()).append('\n');
        out.append("  }\n");
        out.append("}\n");
    }

    private static String propertyName(String name) {
        if (Identifier.matcher(name).matches()) {
            return name;
        }

        return quote(name);
    }

    private static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :inertia4j.typescript:test --tests '*TypeScriptWriterTest'`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml inertia4j.typescript
git commit -m "Add TypeScript generator module with type model and writer"
```

---

### Task 8: Class scanner

**Files:**
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/Annotations.java`
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/GenerationException.java`
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/ClassScanner.java`
- Create fixtures: `inertia4j.typescript/src/test/java/io/github/inertia4j/typescript/fixtures/scan/{ScannedPage,ScannedForm,ScannedShared,Plain}.java`, `.../fixtures/scanother/OtherPage.java`
- Test: `inertia4j.typescript/src/test/java/io/github/inertia4j/typescript/ClassScannerTest.java`

**Interfaces:**
- Produces:
  - `public class GenerationException extends RuntimeException` with `(String)` and `(String, Throwable)` constructors.
  - `final class Annotations` with constants `InertiaPage = "io.github.inertia4j.annotations.InertiaPage"`, `InertiaShared`, `InertiaForm`, `TypeScriptName`, `DeferredProp = "io.github.inertia4j.core.DeferredProp"`, `MergeProp`; methods `static Optional<Annotation> find(AnnotatedElement element, String typeName)`, `static boolean has(AnnotatedElement element, String typeName)`, `static boolean hasSimpleName(AnnotatedElement element, String simpleName)`, `static Optional<Annotation> findSimpleName(AnnotatedElement element, String simpleName)`, `static Object value(Annotation annotation, String method)`.
  - `final class ClassScanner` with `static boolean inPackages(String className, List<String> packages)` and `static Result scan(List<Path> classpath, List<String> packages, ClassLoader loader)`; `record Result(List<Class<?>> annotatedClasses, List<String> warnings)` nested in `ClassScanner`. Classes are sorted by name.

- [ ] **Step 1: Create fixtures**

```java
// fixtures/scan/ScannedPage.java
package io.github.inertia4j.typescript.fixtures.scan;

import io.github.inertia4j.annotations.InertiaPage;

@InertiaPage("Scanned")
public record ScannedPage(String name) {}
```

```java
// fixtures/scan/ScannedForm.java
package io.github.inertia4j.typescript.fixtures.scan;

import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public record ScannedForm(String title) {}
```

```java
// fixtures/scan/ScannedShared.java
package io.github.inertia4j.typescript.fixtures.scan;

import io.github.inertia4j.annotations.InertiaShared;

@InertiaShared
public record ScannedShared(String appName) {}
```

```java
// fixtures/scan/Plain.java
package io.github.inertia4j.typescript.fixtures.scan;

public record Plain(String value) {}
```

```java
// fixtures/scanother/OtherPage.java
package io.github.inertia4j.typescript.fixtures.scanother;

import io.github.inertia4j.annotations.InertiaPage;

@InertiaPage("Other")
public record OtherPage(String name) {}
```

- [ ] **Step 2: Write the failing test**

`ClassScannerTest.java`:

```java
package io.github.inertia4j.typescript;

import io.github.inertia4j.typescript.fixtures.scan.ScannedPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassScannerTest {
    private static final String ScanPackage = "io.github.inertia4j.typescript.fixtures.scan";

    @Test
    void scan_directory_keepsAnnotatedClassesUnderPackages() throws Exception {
        Path classesDir = Path.of(ScannedPage.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        ClassScanner.Result result = ClassScanner.scan(List.of(classesDir), List.of(ScanPackage), getClass().getClassLoader());

        assertEquals(
            List.of(ScanPackage + ".ScannedForm", ScanPackage + ".ScannedPage", ScanPackage + ".ScannedShared"),
            result.annotatedClasses().stream().map(Class::getName).toList()
        );
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void scan_jar_findsAnnotatedClasses(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("fixtures.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            copyClass(out, ScannedPage.class);
        }

        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            ClassScanner.Result result = ClassScanner.scan(List.of(jar), List.of(ScanPackage), loader);

            assertEquals(List.of(ScanPackage + ".ScannedPage"), result.annotatedClasses().stream().map(Class::getName).toList());
        }
    }

    @Test
    void scan_unloadableClass_isSkippedWithWarning(@TempDir Path tempDir) throws Exception {
        Path brokenClass = tempDir.resolve("io/github/inertia4j/typescript/fixtures/broken/Broken.class");
        Files.createDirectories(brokenClass.getParent());
        Files.write(brokenClass, new byte[]{1, 2, 3});

        try (URLClassLoader loader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, getClass().getClassLoader())) {
            ClassScanner.Result result = ClassScanner.scan(
                List.of(tempDir),
                List.of("io.github.inertia4j.typescript.fixtures.broken"),
                loader
            );

            assertTrue(result.annotatedClasses().isEmpty());
            assertEquals(1, result.warnings().size());
            assertTrue(result.warnings().get(0).contains("io.github.inertia4j.typescript.fixtures.broken.Broken"));
        }
    }

    @Test
    void inPackages_matchesPackageAndSubpackagesOnly() {
        assertTrue(ClassScanner.inPackages("com.example.Foo", List.of("com.example")));
        assertTrue(ClassScanner.inPackages("com.example.sub.Foo", List.of("com.example")));
        assertFalse(ClassScanner.inPackages("com.examples.Foo", List.of("com.example")));
    }

    private static void copyClass(JarOutputStream out, Class<?> type) throws IOException {
        String entryName = type.getName().replace('.', '/') + ".class";
        out.putNextEntry(new JarEntry(entryName));
        try (InputStream in = type.getClassLoader().getResourceAsStream(entryName)) {
            in.transferTo(out);
        }
        out.closeEntry();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :inertia4j.typescript:test --tests '*ClassScannerTest'`
Expected: FAIL — `cannot find symbol: class ClassScanner`.

- [ ] **Step 4: Implement `Annotations`**

```java
package io.github.inertia4j.typescript;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Optional;

/**
 * Annotation lookups by name, so user classes loaded in another classloader are matched too.
 */
final class Annotations {
    static final String InertiaPage = "io.github.inertia4j.annotations.InertiaPage";
    static final String InertiaShared = "io.github.inertia4j.annotations.InertiaShared";
    static final String InertiaForm = "io.github.inertia4j.annotations.InertiaForm";
    static final String TypeScriptName = "io.github.inertia4j.annotations.TypeScriptName";
    static final String DeferredProp = "io.github.inertia4j.core.DeferredProp";
    static final String MergeProp = "io.github.inertia4j.core.MergeProp";

    private Annotations() {}

    static Optional<Annotation> find(AnnotatedElement element, String typeName) {
        return Arrays.stream(element.getAnnotations())
            .filter(annotation -> annotation.annotationType().getName().equals(typeName))
            .findFirst();
    }

    static boolean has(AnnotatedElement element, String typeName) {
        return find(element, typeName).isPresent();
    }

    static Optional<Annotation> findSimpleName(AnnotatedElement element, String simpleName) {
        return Arrays.stream(element.getAnnotations())
            .filter(annotation -> annotation.annotationType().getSimpleName().equals(simpleName))
            .findFirst();
    }

    static boolean hasSimpleName(AnnotatedElement element, String simpleName) {
        return findSimpleName(element, simpleName).isPresent();
    }

    static Object value(Annotation annotation, String method) {
        try {
            return annotation.annotationType().getMethod(method).invoke(annotation);
        } catch (ReflectiveOperationException e) {
            throw new GenerationException("Could not read " + method + "() of @" + annotation.annotationType().getSimpleName(), e);
        }
    }
}
```

`GenerationException.java` (needed now):

```java
package io.github.inertia4j.typescript;

/**
 * Thrown when the TypeScript types cannot be generated.
 */
public class GenerationException extends RuntimeException {
    /**
     * @param message description of the problem, naming the class and property involved.
     */
    public GenerationException(String message) {
        super(message);
    }

    /**
     * @param message description of the problem.
     * @param cause   underlying error.
     */
    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 5: Implement `ClassScanner`**

```java
package io.github.inertia4j.typescript;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Finds classes annotated with {@code @InertiaPage}, {@code @InertiaShared} or {@code @InertiaForm}
 * under the configured packages of a classpath.
 */
final class ClassScanner {
    private static final Set<String> RoleAnnotations = Set.of(
        Annotations.InertiaPage,
        Annotations.InertiaShared,
        Annotations.InertiaForm
    );

    record Result(List<Class<?>> annotatedClasses, List<String> warnings) {}

    private ClassScanner() {}

    static boolean inPackages(String className, List<String> packages) {
        return packages.stream().anyMatch(packageName -> className.startsWith(packageName + "."));
    }

    static Result scan(List<Path> classpath, List<String> packages, ClassLoader loader) {
        Set<String> classNames = new TreeSet<>();
        for (Path entry : classpath) {
            classNames.addAll(classNamesIn(entry));
        }

        List<Class<?>> annotatedClasses = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String className : classNames) {
            if (!inPackages(className, packages)) {
                continue;
            }

            try {
                Class<?> type = Class.forName(className, false, loader);
                if (hasRoleAnnotation(type)) {
                    annotatedClasses.add(type);
                }
            } catch (ClassNotFoundException | LinkageError e) {
                warnings.add("Skipped class " + className + ": " + e);
            }
        }

        annotatedClasses.sort(Comparator.comparing(Class::getName));

        return new Result(annotatedClasses, warnings);
    }

    private static boolean hasRoleAnnotation(Class<?> type) {
        return RoleAnnotations.stream().anyMatch(annotation -> Annotations.has(type, annotation));
    }

    private static List<String> classNamesIn(Path entry) {
        if (Files.isDirectory(entry)) {
            return classNamesInDirectory(entry);
        }

        if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
            return classNamesInJar(entry);
        }

        return List.of();
    }

    private static List<String> classNamesInDirectory(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files
                .filter(file -> file.toString().endsWith(".class"))
                .map(file -> toClassName(directory.relativize(file).toString().replace('\\', '/')))
                .filter(ClassScanner::isRegularClassName)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> classNamesInJar(Path jar) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            return jarFile.stream()
                .map(JarEntry::getName)
                .filter(name -> name.endsWith(".class") && !name.startsWith("META-INF/"))
                .map(ClassScanner::toClassName)
                .filter(ClassScanner::isRegularClassName)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String toClassName(String path) {
        return path.substring(0, path.length() - ".class".length()).replace('/', '.');
    }

    private static boolean isRegularClassName(String className) {
        return !className.endsWith("module-info") && !className.endsWith("package-info");
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :inertia4j.typescript:test --tests '*ClassScannerTest'`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add inertia4j.typescript
git commit -m "Scan classpath for Inertia props classes"
```

---

### Task 9: Type mapping and model building (Java classes)

**Files:**
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/GeneratorOptions.java`
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/GenerationResult.java`
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/TypeMapper.java`
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/TypeModelBuilder.java`
- Create: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/TypeScriptGenerator.java`
- Create fixtures under `inertia4j.typescript/src/test/java/io/github/inertia4j/typescript/fixtures/` (listed in Step 1)
- Test: `inertia4j.typescript/src/test/java/io/github/inertia4j/typescript/GeneratorTestSupport.java`
- Test: `inertia4j.typescript/src/test/java/io/github/inertia4j/typescript/TypeMappingTest.java`
- Test: `inertia4j.typescript/src/test/java/io/github/inertia4j/typescript/RolesTest.java`

**Interfaces:**
- Consumes: `ClassScanner.scan`, `ClassScanner.inPackages`, `Annotations.*`, `TsType`, `TsDeclaration`, `TsModel`, `TypeScriptWriter.write`, core `PropertyIntrospector.properties`, `Property`, `PropertyNaming`.
- Produces:
  - `public record GeneratorOptions(List<String> packages, PropertyNaming propertyNaming, ErrorValueType errorValueType, boolean nullableByDefault)`.
  - `public record GenerationResult(String content, List<String> warnings)`.
  - `public final class TypeScriptGenerator { public static GenerationResult generate(GeneratorOptions options, List<Path> classpath) }` — throws `GenerationException` for fatal problems.
  - `TypeModelBuilder(GeneratorOptions options)` with `TsModel build(List<Class<?>> roleClasses)` and `List<String> warnings()`.
  - `TypeModelBuilder` exposes a hook used by Task 10: `private Set<String> kotlinNullableAccessors(Class<?> type)` returning `null` for now.

- [ ] **Step 1: Create fixtures**

Each fixture package is isolated so one test's fatal error cannot affect another. All files start with `package io.github.inertia4j.typescript.fixtures.<case>;`.

`builtins/BuiltIns.java`:

```java
package io.github.inertia4j.typescript.fixtures.builtins;

import io.github.inertia4j.annotations.InertiaForm;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@InertiaForm
public record BuiltIns(
    String text,
    char letter,
    UUID id,
    URI uri,
    int count,
    long big,
    double ratio,
    BigDecimal price,
    boolean active,
    Boolean flag,
    Instant createdAt,
    LocalDate day,
    Duration timeout,
    byte[] data,
    Object anything
) {}
```

`collections/Collections.java`:

```java
package io.github.inertia4j.typescript.fixtures.collections;

import io.github.inertia4j.annotations.InertiaForm;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@InertiaForm
public record Collections(
    List<String> names,
    Set<Integer> ids,
    String[] tags,
    int[][] grid,
    Map<String, Integer> counts,
    Map<Long, String> byId,
    Optional<String> nickname,
    List<? extends Item> items,
    List<?> anything
) {}
```

`collections/Item.java`:

```java
package io.github.inertia4j.typescript.fixtures.collections;

public record Item(String name) {}
```

`enums/Status.java`, `enums/Priority.java`, `enums/Task.java`:

```java
package io.github.inertia4j.typescript.fixtures.enums;

public enum Status { Active, Archived }
```

```java
package io.github.inertia4j.typescript.fixtures.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Priority {
    LOW,
    HIGH;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
```

```java
package io.github.inertia4j.typescript.fixtures.enums;

import io.github.inertia4j.annotations.InertiaForm;

import java.util.Map;

@InertiaForm
public record Task(Status status, Priority priority, Map<Status, Integer> countByStatus) {}
```

`generics/Page.java`, `generics/Item.java`, `generics/ItemsIndex.java`:

```java
package io.github.inertia4j.typescript.fixtures.generics;

import java.util.List;

public record Page<T>(List<T> items, int total) {}
```

```java
package io.github.inertia4j.typescript.fixtures.generics;

public record Item(String name) {}
```

```java
package io.github.inertia4j.typescript.fixtures.generics;

import io.github.inertia4j.annotations.InertiaPage;

@InertiaPage("Items/Index")
public record ItemsIndex(Page<Item> page, Page rawPage) {}
```

`wrappers/Stat.java`, `wrappers/StatsProps.java`:

```java
package io.github.inertia4j.typescript.fixtures.wrappers;

public record Stat(String label) {}
```

```java
package io.github.inertia4j.typescript.fixtures.wrappers;

import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.core.DeferredProp;
import io.github.inertia4j.core.MergeProp;

import java.util.List;
import java.util.function.Supplier;

@InertiaPage("Stats")
public record StatsProps(
    DeferredProp<List<Stat>> stats,
    MergeProp<List<Stat>> feed,
    Supplier<Integer> total,
    DeferredProp<MergeProp<List<Stat>>> history
) {}
```

`nullability/Profile.java`, `nullability/Compact.java`:

```java
package io.github.inertia4j.typescript.fixtures.nullability;

import io.github.inertia4j.annotations.InertiaForm;
import org.jspecify.annotations.Nullable;

@InertiaForm
public record Profile(@Nullable String nickname, String name, int age) {}
```

```java
package io.github.inertia4j.typescript.fixtures.nullability;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.inertia4j.annotations.InertiaForm;
import org.jspecify.annotations.Nullable;

@InertiaForm
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Compact(@Nullable String nickname, String name) {}
```

`nullbydefault/Account.java`, `nullbydefault/Marked.java`:

```java
package io.github.inertia4j.typescript.fixtures.nullbydefault;

import io.github.inertia4j.annotations.InertiaForm;
import org.jspecify.annotations.NonNull;

@InertiaForm
public record Account(String email, @NonNull String id, int age) {}
```

```java
package io.github.inertia4j.typescript.fixtures.nullbydefault;

import io.github.inertia4j.annotations.InertiaForm;
import org.jspecify.annotations.NullMarked;

@InertiaForm
@NullMarked
public record Marked(String name) {}
```

`naming/Renamed.java`:

```java
package io.github.inertia4j.typescript.fixtures.naming;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public record Renamed(@JsonProperty("full_name") String name, @JsonIgnore String secret, String firstName) {}
```

`inheritance/Base.java`, `inheritance/Child.java`:

```java
package io.github.inertia4j.typescript.fixtures.inheritance;

public class Base {
    private long id;

    public long getId() {
        return id;
    }
}
```

```java
package io.github.inertia4j.typescript.fixtures.inheritance;

import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public class Child extends Base {
    private String name;

    public String getName() {
        return name;
    }
}
```

`external/UsesExternal.java`:

```java
package io.github.inertia4j.typescript.fixtures.external;

import io.github.inertia4j.annotations.InertiaForm;

import java.util.Locale;

@InertiaForm
public record UsesExternal(Locale locale) {}
```

`collision/a/Item.java`, `collision/b/Item.java`, `collision/Both.java`:

```java
package io.github.inertia4j.typescript.fixtures.collision.a;

public record Item(String name) {}
```

```java
package io.github.inertia4j.typescript.fixtures.collision.b;

public record Item(int id) {}
```

```java
package io.github.inertia4j.typescript.fixtures.collision;

import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public record Both(
    io.github.inertia4j.typescript.fixtures.collision.a.Item first,
    io.github.inertia4j.typescript.fixtures.collision.b.Item second
) {}
```

`renamed/a/Item.java`, `renamed/b/Item.java`, `renamed/Both.java`:

```java
package io.github.inertia4j.typescript.fixtures.renamed.a;

public record Item(String name) {}
```

```java
package io.github.inertia4j.typescript.fixtures.renamed.b;

import io.github.inertia4j.annotations.TypeScriptName;

@TypeScriptName("OtherItem")
public record Item(int id) {}
```

```java
package io.github.inertia4j.typescript.fixtures.renamed;

import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public record Both(
    io.github.inertia4j.typescript.fixtures.renamed.a.Item first,
    io.github.inertia4j.typescript.fixtures.renamed.b.Item second
) {}
```

`reserved/InertiaPages.java`:

```java
package io.github.inertia4j.typescript.fixtures.reserved;

import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public record InertiaPages(String name) {}
```

`badkey/BadKey.java`:

```java
package io.github.inertia4j.typescript.fixtures.badkey;

import io.github.inertia4j.annotations.InertiaForm;

import java.util.List;
import java.util.Map;

@InertiaForm
public record BadKey(Map<List<String>, String> weird) {}
```

`roles/AppShared.java`, `roles/AuthShared.java`, `roles/HomeProps.java`, `roles/UsersIndexProps.java`:

```java
package io.github.inertia4j.typescript.fixtures.roles;

import io.github.inertia4j.annotations.InertiaShared;

@InertiaShared
public record AppShared(String appName) {}
```

```java
package io.github.inertia4j.typescript.fixtures.roles;

import io.github.inertia4j.annotations.InertiaShared;

@InertiaShared
public record AuthShared(boolean loggedIn) {}
```

```java
package io.github.inertia4j.typescript.fixtures.roles;

import io.github.inertia4j.annotations.InertiaPage;

@InertiaPage("Home")
public record HomeProps(String title) {}
```

```java
package io.github.inertia4j.typescript.fixtures.roles;

import io.github.inertia4j.annotations.InertiaPage;

@InertiaPage("Users/Index")
public record UsersIndexProps(int count) {}
```

`duplicatepage/FirstHome.java`, `duplicatepage/SecondHome.java`:

```java
package io.github.inertia4j.typescript.fixtures.duplicatepage;

import io.github.inertia4j.annotations.InertiaPage;

@InertiaPage("Home")
public record FirstHome(String title) {}
```

```java
package io.github.inertia4j.typescript.fixtures.duplicatepage;

import io.github.inertia4j.annotations.InertiaPage;

@InertiaPage("Home")
public record SecondHome(String title) {}
```

`blankpage/Blank.java`:

```java
package io.github.inertia4j.typescript.fixtures.blankpage;

import io.github.inertia4j.annotations.InertiaPage;

@InertiaPage(" ")
public record Blank(String title) {}
```

- [ ] **Step 2: Write test support and failing tests**

`GeneratorTestSupport.java`:

```java
package io.github.inertia4j.typescript;

import io.github.inertia4j.core.PropertyNaming;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeneratorTestSupport {
    private GeneratorTestSupport() {}

    static GeneratorOptions options(String fixturePackage) {
        return new GeneratorOptions(
            List.of("io.github.inertia4j.typescript.fixtures." + fixturePackage),
            PropertyNaming.Camel,
            ErrorValueType.String,
            false
        );
    }

    static GenerationResult generate(String fixturePackage) {
        return generate(options(fixturePackage));
    }

    static GenerationResult generate(GeneratorOptions options) {
        return TypeScriptGenerator.generate(options, testClasspath());
    }

    static List<Path> testClasspath() {
        return Arrays.stream(System.getProperty("inertia4j.test.classpath").split(File.pathSeparator))
            .map(Path::of)
            .toList();
    }

    static void assertContains(String content, String expectedBlock) {
        assertTrue(content.contains(expectedBlock), () -> "Expected:\n" + expectedBlock + "\n\nin:\n" + content);
    }
}
```

`TypeMappingTest.java`:

```java
package io.github.inertia4j.typescript;

import io.github.inertia4j.core.PropertyNaming;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.inertia4j.typescript.GeneratorTestSupport.assertContains;
import static io.github.inertia4j.typescript.GeneratorTestSupport.generate;
import static io.github.inertia4j.typescript.GeneratorTestSupport.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeMappingTest {
    @Test
    void builtIns() {
        GenerationResult result = generate("builtins");

        assertContains(result.content(), """
            export interface BuiltIns {
              text: string
              letter: string
              id: string
              uri: string
              count: number
              big: number
              ratio: number
              price: number
              active: boolean
              flag: boolean
              createdAt: string
              day: string
              timeout: string
              data: string
              anything: unknown
            }
            """);
        assertTrue(result.warnings().isEmpty(), result.warnings()::toString);
    }

    @Test
    void collections() {
        GenerationResult result = generate("collections");

        assertContains(result.content(), """
            export interface Collections {
              names: string[]
              ids: number[]
              tags: string[]
              grid: number[][]
              counts: { [key: string]: number }
              byId: { [key: number]: string }
              nickname: string | null
              items: Item[]
              anything: unknown[]
            }
            """);
        assertContains(result.content(), """
            export interface Item {
              name: string
            }
            """);
        assertEquals(1, result.warnings().size(), result.warnings()::toString);
        assertTrue(result.warnings().get(0).contains("Collections.anything"));
    }

    @Test
    void enums() {
        GenerationResult result = generate("enums");

        assertContains(result.content(), "export type Priority = 'low' | 'high'\n");
        assertContains(result.content(), "export type Status = 'Active' | 'Archived'\n");
        assertContains(result.content(), """
            export interface Task {
              status: Status
              priority: Priority
              countByStatus: { [key in Status]?: number }
            }
            """);
    }

    @Test
    void generics() {
        GenerationResult result = generate("generics");

        assertContains(result.content(), """
            export interface Page<T> {
              items: T[]
              total: number
            }
            """);
        assertContains(result.content(), """
            export interface ItemsIndex {
              page: Page<Item>
              rawPage: Page<unknown>
            }
            """);
    }

    @Test
    void inertiaWrappers() {
        GenerationResult result = generate("wrappers");

        assertContains(result.content(), """
            export interface StatsProps {
              stats?: Stat[]
              feed: Stat[]
              total: number
              history?: Stat[]
            }
            """);
    }

    @Test
    void nullability() {
        GenerationResult result = generate("nullability");

        assertContains(result.content(), """
            export interface Profile {
              nickname: string | null
              name: string
              age: number
            }
            """);
        assertContains(result.content(), """
            export interface Compact {
              nickname?: string
              name: string
            }
            """);
    }

    @Test
    void nullableByDefault() {
        GeneratorOptions defaults = options("nullbydefault");
        GenerationResult result = generate(new GeneratorOptions(
            defaults.packages(),
            defaults.propertyNaming(),
            defaults.errorValueType(),
            true
        ));

        assertContains(result.content(), """
            export interface Account {
              email: string | null
              id: string
              age: number
            }
            """);
        assertContains(result.content(), """
            export interface Marked {
              name: string
            }
            """);
    }

    @Test
    void naming() {
        GeneratorOptions defaults = options("naming");
        GenerationResult result = generate(new GeneratorOptions(
            defaults.packages(),
            PropertyNaming.Snake,
            defaults.errorValueType(),
            false
        ));

        assertContains(result.content(), """
            export interface Renamed {
              full_name: string
              first_name: string
            }
            """);
    }

    @Test
    void inheritance_isFlattened() {
        GenerationResult result = generate("inheritance");

        assertContains(result.content(), """
            export interface Child {
              id: number
              name: string
            }
            """);
        assertFalse(result.content().contains("export interface Base"));
    }

    @Test
    void typeOutsidePackages_isUnknownWithWarning() {
        GenerationResult result = generate("external");

        assertContains(result.content(), """
            export interface UsesExternal {
              locale: unknown
            }
            """);
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("java.util.Locale"));
        assertTrue(result.warnings().get(0).contains("UsesExternal.locale"));
    }

    @Test
    void duplicateTypeName_isRejected() {
        GenerationException exception = assertThrows(GenerationException.class, () -> generate("collision"));

        assertTrue(exception.getMessage().contains("fixtures.collision.a.Item"));
        assertTrue(exception.getMessage().contains("fixtures.collision.b.Item"));
        assertTrue(exception.getMessage().contains("@TypeScriptName"));
    }

    @Test
    void typeScriptName_resolvesCollision() {
        GenerationResult result = generate("renamed");

        assertContains(result.content(), """
            export interface Both {
              first: Item
              second: OtherItem
            }
            """);
        assertContains(result.content(), "export interface OtherItem {\n  id: number\n}\n");
    }

    @Test
    void reservedName_isRejected() {
        GenerationException exception = assertThrows(GenerationException.class, () -> generate("reserved"));

        assertTrue(exception.getMessage().contains("InertiaPages"));
    }

    @Test
    void unsupportedMapKey_isRejected() {
        GenerationException exception = assertThrows(GenerationException.class, () -> generate("badkey"));

        assertTrue(exception.getMessage().contains("BadKey.weird"));
    }

    @Test
    void emptyPackages_isRejected() {
        GeneratorOptions noPackages = new GeneratorOptions(List.of(), PropertyNaming.Camel, ErrorValueType.String, false);

        assertThrows(GenerationException.class, () -> generate(noPackages));
    }
}
```

`RolesTest.java`:

```java
package io.github.inertia4j.typescript;

import org.junit.jupiter.api.Test;

import static io.github.inertia4j.typescript.GeneratorTestSupport.assertContains;
import static io.github.inertia4j.typescript.GeneratorTestSupport.generate;
import static io.github.inertia4j.typescript.GeneratorTestSupport.options;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolesTest {
    @Test
    void pagesAndSharedProps() {
        String content = generate("roles").content();

        assertContains(content, """
            export interface InertiaPages {
              'Home': HomeProps
              'Users/Index': UsersIndexProps
            }
            """);
        assertContains(content, "    sharedPageProps: AppShared & AuthShared\n");
        assertContains(content, "    errorValueType: string\n");
    }

    @Test
    void errorValueType_stringArray() {
        GeneratorOptions defaults = options("roles");
        String content = generate(new GeneratorOptions(
            defaults.packages(),
            defaults.propertyNaming(),
            ErrorValueType.StringArray,
            false
        )).content();

        assertContains(content, "    errorValueType: string[]\n");
    }

    @Test
    void withoutSharedClasses_sharedPagePropsIsOmitted() {
        assertFalse(generate("builtins").content().contains("sharedPageProps"));
    }

    @Test
    void duplicateComponent_isRejected() {
        GenerationException exception = assertThrows(GenerationException.class, () -> generate("duplicatepage"));

        assertTrue(exception.getMessage().contains("FirstHome"));
        assertTrue(exception.getMessage().contains("SecondHome"));
        assertTrue(exception.getMessage().contains("'Home'"));
    }

    @Test
    void blankComponent_isRejected() {
        GenerationException exception = assertThrows(GenerationException.class, () -> generate("blankpage"));

        assertTrue(exception.getMessage().contains("Blank"));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :inertia4j.typescript:test --tests '*TypeMappingTest' --tests '*RolesTest'`
Expected: FAIL — `GeneratorOptions`, `TypeScriptGenerator` not found.

- [ ] **Step 4: Implement options and result**

`GeneratorOptions.java`:

```java
package io.github.inertia4j.typescript;

import io.github.inertia4j.core.PropertyNaming;

import java.util.List;

/**
 * Options of a TypeScript generation run.
 *
 * @param packages          package roots to scan; only classes under them get their own declaration.
 * @param propertyNaming    naming strategy for properties without explicit name.
 * @param errorValueType    type of validation error values.
 * @param nullableByDefault whether unannotated Java properties are nullable.
 */
public record GeneratorOptions(
    List<String> packages,
    PropertyNaming propertyNaming,
    ErrorValueType errorValueType,
    boolean nullableByDefault
) {}
```

`GenerationResult.java`:

```java
package io.github.inertia4j.typescript;

import java.util.List;

/**
 * Output of a TypeScript generation run.
 *
 * @param content  content of the {@code .d.ts} file.
 * @param warnings non-fatal problems, e.g. types mapped to {@code unknown}.
 */
public record GenerationResult(String content, List<String> warnings) {}
```

- [ ] **Step 5: Implement `TypeMapper`**

```java
package io.github.inertia4j.typescript;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps Java reflection types to TypeScript types. User classes are resolved through the {@code references}
 * callback, which registers them for emission and returns their TypeScript name.
 */
final class TypeMapper {
    private static final Map<String, TsType> BuiltIns = Map.ofEntries(
        Map.entry("java.lang.String", TsType.StringKeyword),
        Map.entry("char", TsType.StringKeyword),
        Map.entry("java.lang.Character", TsType.StringKeyword),
        Map.entry("java.util.UUID", TsType.StringKeyword),
        Map.entry("java.net.URI", TsType.StringKeyword),
        Map.entry("java.net.URL", TsType.StringKeyword),
        Map.entry("java.time.Instant", TsType.StringKeyword),
        Map.entry("java.time.LocalDate", TsType.StringKeyword),
        Map.entry("java.time.LocalDateTime", TsType.StringKeyword),
        Map.entry("java.time.OffsetDateTime", TsType.StringKeyword),
        Map.entry("java.time.ZonedDateTime", TsType.StringKeyword),
        Map.entry("java.time.LocalTime", TsType.StringKeyword),
        Map.entry("java.time.Duration", TsType.StringKeyword),
        Map.entry("int", TsType.NumberKeyword),
        Map.entry("long", TsType.NumberKeyword),
        Map.entry("short", TsType.NumberKeyword),
        Map.entry("byte", TsType.NumberKeyword),
        Map.entry("double", TsType.NumberKeyword),
        Map.entry("float", TsType.NumberKeyword),
        Map.entry("java.lang.Integer", TsType.NumberKeyword),
        Map.entry("java.lang.Long", TsType.NumberKeyword),
        Map.entry("java.lang.Short", TsType.NumberKeyword),
        Map.entry("java.lang.Byte", TsType.NumberKeyword),
        Map.entry("java.lang.Double", TsType.NumberKeyword),
        Map.entry("java.lang.Float", TsType.NumberKeyword),
        Map.entry("java.math.BigDecimal", TsType.NumberKeyword),
        Map.entry("java.math.BigInteger", TsType.NumberKeyword),
        Map.entry("boolean", TsType.BooleanKeyword),
        Map.entry("java.lang.Boolean", TsType.BooleanKeyword)
    );

    private static final Set<String> SilentUnknowns = Set.of(
        "java.lang.Object",
        "com.fasterxml.jackson.databind.JsonNode",
        "tools.jackson.databind.JsonNode"
    );

    private static final Set<String> Wrappers = Set.of(
        Annotations.DeferredProp,
        Annotations.MergeProp,
        "java.util.function.Supplier",
        "kotlin.jvm.functions.Function0"
    );

    interface References {
        String register(Class<?> type);
    }

    private final List<String> packages;
    private final References references;
    private final List<String> warnings;
    private String context = "";

    TypeMapper(List<String> packages, References references, List<String> warnings) {
        this.packages = packages;
        this.references = references;
        this.warnings = warnings;
    }

    /**
     * @param context {@code Owner.property} used in warnings and errors.
     */
    void setContext(String context) {
        this.context = context;
    }

    static boolean isDeferred(Type type) {
        return rawClass(type) != null && rawClass(type).getName().equals(Annotations.DeferredProp);
    }

    TsType map(Type type) {
        if (type instanceof Class<?> typeClass) {
            return mapClass(typeClass);
        }

        if (type instanceof ParameterizedType parameterized) {
            return mapParameterized(parameterized);
        }

        if (type instanceof GenericArrayType array) {
            return new TsType.Array(map(array.getGenericComponentType()));
        }

        if (type instanceof TypeVariable<?> variable) {
            return new TsType.Variable(variable.getName());
        }

        if (type instanceof WildcardType wildcard) {
            return mapWildcard(wildcard);
        }

        return unknown(type.getTypeName());
    }

    private TsType mapClass(Class<?> type) {
        if (type == byte[].class) {
            return TsType.StringKeyword;
        }

        if (type.isArray()) {
            return new TsType.Array(mapClass(type.getComponentType()));
        }

        TsType builtIn = BuiltIns.get(type.getName());
        if (builtIn != null) {
            return builtIn;
        }

        if (SilentUnknowns.contains(type.getName())) {
            return TsType.UnknownKeyword;
        }

        if (Wrappers.contains(type.getName())) {
            return TsType.UnknownKeyword;
        }

        if (type.getName().equals("java.util.Optional")) {
            return TsType.UnknownKeyword;
        }

        if (Collection.class.isAssignableFrom(type)) {
            return new TsType.Array(TsType.UnknownKeyword);
        }

        if (Map.class.isAssignableFrom(type)) {
            return new TsType.StringMap(TsType.UnknownKeyword);
        }

        if (isUserType(type)) {
            List<TsType> rawArguments = Arrays.stream(type.getTypeParameters())
                .map(parameter -> TsType.UnknownKeyword)
                .toList();
            return new TsType.Reference(references.register(type), rawArguments);
        }

        return unknown(type.getName());
    }

    private TsType mapParameterized(ParameterizedType parameterized) {
        Class<?> raw = (Class<?>) parameterized.getRawType();
        Type[] arguments = parameterized.getActualTypeArguments();

        if (Wrappers.contains(raw.getName())) {
            return map(arguments[0]);
        }

        if (raw.getName().equals("java.util.Optional")) {
            return TsType.nullable(map(arguments[0]));
        }

        if (Collection.class.isAssignableFrom(raw)) {
            return new TsType.Array(map(arguments[0]));
        }

        if (Map.class.isAssignableFrom(raw)) {
            return mapMap(arguments[0], arguments[1]);
        }

        if (isUserType(raw)) {
            List<TsType> mappedArguments = Arrays.stream(arguments).map(this::map).toList();
            return new TsType.Reference(references.register(raw), mappedArguments);
        }

        return unknown(raw.getName());
    }

    private TsType mapMap(Type keyType, Type valueType) {
        TsType value = map(valueType);
        TsType key = map(keyType);

        if (key == TsType.StringKeyword) {
            return new TsType.StringMap(value);
        }

        if (key == TsType.NumberKeyword) {
            return new TsType.NumberMap(value);
        }

        Class<?> keyClass = rawClass(keyType);
        if (keyClass != null && keyClass.isEnum()) {
            return key instanceof TsType.Reference
                ? new TsType.EnumMap(key, value)
                : new TsType.StringMap(value);
        }

        throw new GenerationException(
            "Unsupported map key type " + keyType.getTypeName() + " in " + context
                + ": keys must be strings, numbers or enums"
        );
    }

    private TsType mapWildcard(WildcardType wildcard) {
        Type[] upperBounds = wildcard.getUpperBounds();

        if (wildcard.getLowerBounds().length == 0 && upperBounds.length == 1 && upperBounds[0] != Object.class) {
            return map(upperBounds[0]);
        }

        return unknown(wildcard.getTypeName());
    }

    private boolean isUserType(Class<?> type) {
        return ClassScanner.inPackages(type.getName(), packages);
    }

    private TsType unknown(String typeName) {
        warnings.add("Type " + typeName + " in " + context + " is outside the configured packages and is mapped to unknown");
        return TsType.UnknownKeyword;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> typeClass) {
            return typeClass;
        }

        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }

        return null;
    }
}
```

Notes for the implementer:
- `unknown(...)` for a wildcard such as `?` produces the warning required by `collections` (`Collections.anything`).
- `mapClass` for an enum outside the packages goes to `unknown`, but as a *map key* such an enum becomes a `StringMap` (see `mapMap`) — the key call to `map` would already have added a warning; that is acceptable.

- [ ] **Step 6: Implement `TypeModelBuilder`**

```java
package io.github.inertia4j.typescript;

import io.github.inertia4j.core.Property;
import io.github.inertia4j.core.PropertyIntrospector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Builds the {@link TsModel} for the role classes found by the {@link ClassScanner},
 * emitting every referenced user type transitively.
 */
final class TypeModelBuilder {
    private static final Set<String> ReservedNames = Set.of("InertiaPages", "PageProps");
    private static final Set<String> OmitNullInclusions = Set.of("NON_NULL", "NON_ABSENT", "NON_EMPTY");

    private final GeneratorOptions options;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Class<?>> classesByName = new HashMap<>();
    private final Map<Class<?>, String> namesByClass = new HashMap<>();
    private final Deque<Class<?>> pending = new ArrayDeque<>();
    private final TypeMapper mapper;

    TypeModelBuilder(GeneratorOptions options) {
        this.options = options;
        this.mapper = new TypeMapper(options.packages(), this::register, warnings);
    }

    List<String> warnings() {
        return List.copyOf(new LinkedHashSet<>(warnings));
    }

    TsModel build(List<Class<?>> roleClasses) {
        roleClasses.forEach(this::register);

        List<TsDeclaration> declarations = new ArrayList<>();
        while (!pending.isEmpty()) {
            declarations.add(declare(pending.poll()));
        }

        return new TsModel(declarations, pages(roleClasses), sharedTypes(roleClasses), options.errorValueType());
    }

    private String register(Class<?> type) {
        String existing = namesByClass.get(type);
        if (existing != null) {
            return existing;
        }

        String name = typeScriptName(type);

        if (ReservedNames.contains(name)) {
            throw new GenerationException(
                "Type name '" + name + "' of " + type.getName() + " is reserved by the generated file. Rename it with @TypeScriptName."
            );
        }

        Class<?> other = classesByName.get(name);
        if (other != null) {
            throw new GenerationException(
                "Type name '" + name + "' is used by both " + other.getName() + " and " + type.getName()
                    + ". Rename one of them with @TypeScriptName."
            );
        }

        classesByName.put(name, type);
        namesByClass.put(type, name);
        pending.add(type);

        return name;
    }

    private static String typeScriptName(Class<?> type) {
        return Annotations.find(type, Annotations.TypeScriptName)
            .map(annotation -> (String) Annotations.value(annotation, "value"))
            .orElse(type.getSimpleName());
    }

    private TsDeclaration declare(Class<?> type) {
        String name = namesByClass.get(type);

        if (type.isEnum()) {
            return new TsDeclaration.Enum(name, enumValues(type));
        }

        List<String> typeParameters = Arrays.stream(type.getTypeParameters()).map(TypeVariable::getName).toList();
        Set<String> kotlinNullable = kotlinNullableAccessors(type);
        List<TsDeclaration.Property> properties = new ArrayList<>();

        for (Property property : PropertyIntrospector.properties(type, options.propertyNaming())) {
            properties.add(declareProperty(type, property, kotlinNullable));
        }

        return new TsDeclaration.Interface(name, typeParameters, properties);
    }

    private TsDeclaration.Property declareProperty(Class<?> owner, Property property, Set<String> kotlinNullable) {
        mapper.setContext(owner.getSimpleName() + "." + property.getName());

        Type type = property.getGenericType();
        TsType tsType = mapper.map(type);
        boolean optional = TypeMapper.isDeferred(type);

        if (isPrimitive(type) || !isNullable(owner, property, kotlinNullable)) {
            return new TsDeclaration.Property(property.getName(), tsType, optional);
        }

        if (omitsNulls(owner, property)) {
            return new TsDeclaration.Property(property.getName(), tsType, true);
        }

        return new TsDeclaration.Property(property.getName(), TsType.nullable(tsType), optional);
    }

    private boolean isNullable(Class<?> owner, Property property, Set<String> kotlinNullable) {
        if (kotlinNullable != null) {
            return kotlinNullable.contains(property.getAccessorName());
        }

        if (property.hasAnnotation("Nullable")) {
            return true;
        }

        if (!options.nullableByDefault()) {
            return false;
        }

        if (property.hasAnnotation("NonNull") || property.hasAnnotation("NotNull")) {
            return false;
        }

        return !isNullMarked(owner);
    }

    private static boolean isNullMarked(Class<?> owner) {
        for (Class<?> current = owner; current != null; current = current.getEnclosingClass()) {
            if (Annotations.hasSimpleName(current, "NullMarked")) {
                return true;
            }
        }

        Package ownerPackage = owner.getPackage();

        return ownerPackage != null && Annotations.hasSimpleName(ownerPackage, "NullMarked");
    }

    private static boolean omitsNulls(Class<?> owner, Property property) {
        Optional<Annotation> include = property.findAnnotation("JsonInclude")
            .or(() -> Annotations.findSimpleName(owner, "JsonInclude"));

        return include
            .map(annotation -> ((Enum<?>) Annotations.value(annotation, "value")).name())
            .filter(OmitNullInclusions::contains)
            .isPresent();
    }

    private static boolean isPrimitive(Type type) {
        return type instanceof Class<?> typeClass && typeClass.isPrimitive();
    }

    private static List<String> enumValues(Class<?> type) {
        Optional<Method> jsonValue = Arrays.stream(type.getDeclaredMethods())
            .filter(method -> Annotations.hasSimpleName(method, "JsonValue"))
            .findFirst();

        List<String> values = new ArrayList<>();
        for (Object constant : type.getEnumConstants()) {
            values.add(jsonValue.isPresent() ? String.valueOf(invoke(jsonValue.get(), constant)) : ((Enum<?>) constant).name());
        }

        return values;
    }

    private static Object invoke(Method method, Object target) {
        try {
            method.trySetAccessible();
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new GenerationException("Could not read @JsonValue of " + target.getClass().getName(), e);
        }
    }

    private SortedMap<String, String> pages(List<Class<?>> roleClasses) {
        SortedMap<String, String> pages = new TreeMap<>();
        Map<String, Class<?>> pageClasses = new HashMap<>();

        for (Class<?> type : roleClasses) {
            Optional<Annotation> page = Annotations.find(type, Annotations.InertiaPage);
            if (page.isEmpty()) {
                continue;
            }

            String component = (String) Annotations.value(page.get(), "value");
            if (component.isBlank()) {
                throw new GenerationException("@InertiaPage of " + type.getName() + " has a blank component name");
            }

            Class<?> other = pageClasses.put(component, type);
            if (other != null) {
                throw new GenerationException(
                    "Component '" + component + "' is declared by both " + other.getName() + " and " + type.getName()
                );
            }

            pages.put(component, namesByClass.get(type));
        }

        return pages;
    }

    private List<String> sharedTypes(List<Class<?>> roleClasses) {
        return roleClasses.stream()
            .filter(type -> Annotations.has(type, Annotations.InertiaShared))
            .map(namesByClass::get)
            .sorted()
            .toList();
    }

    private Set<String> kotlinNullableAccessors(Class<?> type) {
        return null;
    }
}
```

- [ ] **Step 7: Implement `TypeScriptGenerator`**

```java
package io.github.inertia4j.typescript;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Generates the TypeScript declaration file for the Inertia props classes of a classpath.
 * <p>
 * User classes are loaded in an isolated classloader (parent: platform classloader), so the generator
 * works whatever the versions of Inertia4J, Jackson or Kotlin used by the application.
 */
public final class TypeScriptGenerator {
    private TypeScriptGenerator() {}

    /**
     * @param options   generation options.
     * @param classpath runtime classpath of the application: class directories and jars.
     * @return generated content and warnings.
     * @throws GenerationException on fatal problems (name collisions, duplicate components, unsupported map keys, …).
     */
    public static GenerationResult generate(GeneratorOptions options, List<Path> classpath) {
        if (options.packages().isEmpty()) {
            throw new GenerationException("No packages configured: list the packages holding your props classes");
        }

        try (URLClassLoader loader = new URLClassLoader(toUrls(classpath), ClassLoader.getPlatformClassLoader())) {
            ClassScanner.Result scan = ClassScanner.scan(classpath, options.packages(), loader);
            TypeModelBuilder builder = new TypeModelBuilder(options);
            TsModel model = builder.build(scan.annotatedClasses());

            List<String> warnings = new ArrayList<>(scan.warnings());
            warnings.addAll(builder.warnings());

            return new GenerationResult(TypeScriptWriter.write(model), List.copyOf(new LinkedHashSet<>(warnings)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static URL[] toUrls(List<Path> classpath) {
        URL[] urls = new URL[classpath.size()];

        for (int i = 0; i < classpath.size(); i++) {
            try {
                urls[i] = classpath.get(i).toUri().toURL();
            } catch (MalformedURLException e) {
                throw new GenerationException("Invalid classpath entry " + classpath.get(i), e);
            }
        }

        return urls;
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :inertia4j.typescript:test`
Expected: PASS (writer, scanner, mapping and roles tests). If `collections` reports more than one warning, print `result.warnings()` and fix the mapper — each unknown type must warn once per property.

- [ ] **Step 9: Commit**

```bash
git add inertia4j.typescript
git commit -m "Map Java props classes to TypeScript declarations"
```

---

### Task 10: Kotlin nullability

**Files:**
- Create: `inertia4j.typescript/src/main/kotlin/io/github/inertia4j/typescript/KotlinNullability.kt`
- Modify: `inertia4j.typescript/src/main/java/io/github/inertia4j/typescript/TypeModelBuilder.java` (`kotlinNullableAccessors`)
- Create fixture: `inertia4j.typescript/src/test/kotlin/io/github/inertia4j/typescript/fixtures/kotlin/UserProps.kt`
- Test: `inertia4j.typescript/src/test/java/io/github/inertia4j/typescript/KotlinTest.java`

**Interfaces:**
- Consumes: `TypeModelBuilder.kotlinNullableAccessors(Class<?>)` hook.
- Produces: `KotlinNullability.nullableAccessors(Class<?> type): Set<String>?` (Java: `KotlinNullability.nullableAccessors(type)`), returning getter method names of nullable Kotlin properties, or `null` when the class is not a Kotlin class.

- [ ] **Step 1: Create the Kotlin fixture**

```kotlin
package io.github.inertia4j.typescript.fixtures.kotlin

import io.github.inertia4j.annotations.InertiaPage

@InertiaPage("Users/Show")
data class UserProps(
    val name: String,
    val nickname: String?,
    val tags: List<String>,
    val count: () -> Int,
    val age: Int?,
)
```

- [ ] **Step 2: Write the failing test**

`KotlinTest.java`:

```java
package io.github.inertia4j.typescript;

import org.junit.jupiter.api.Test;

import static io.github.inertia4j.typescript.GeneratorTestSupport.assertContains;
import static io.github.inertia4j.typescript.GeneratorTestSupport.generate;

class KotlinTest {
    @Test
    void kotlinNullability_isReadFromMetadata() {
        GenerationResult result = generate("kotlin");

        assertContains(result.content(), """
            export interface UserProps {
              name: string
              nickname: string | null
              tags: string[]
              count: number
              age: number | null
            }
            """);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :inertia4j.typescript:test --tests '*KotlinTest'`
Expected: FAIL — `nickname: string` (no `| null`) because the hook returns `null`.

- [ ] **Step 4: Implement `KotlinNullability`**

```kotlin
package io.github.inertia4j.typescript

import kotlin.metadata.isNullable
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.Metadata
import kotlin.metadata.jvm.getterSignature

/**
 * Reads property nullability of Kotlin classes from their `kotlin.Metadata` annotation.
 * The annotation is read reflectively because user classes live in another classloader.
 */
internal object KotlinNullability {
    private const val MetadataAnnotation = "kotlin.Metadata"

    /**
     * @return getter names of nullable properties, or `null` if [type] is not a Kotlin class.
     */
    @JvmStatic
    fun nullableAccessors(type: Class<*>): Set<String>? {
        val annotation = type.annotations.firstOrNull { it.annotationType().name == MetadataAnnotation } ?: return null

        val metadata = Metadata(
            kind = annotation.read("k"),
            metadataVersion = annotation.read("mv"),
            data1 = annotation.read("d1"),
            data2 = annotation.read("d2"),
            extraString = annotation.read("xs"),
            packageName = annotation.read("pn"),
            extraInt = annotation.read("xi"),
        )

        val classMetadata = KotlinClassMetadata.readLenient(metadata) as? KotlinClassMetadata.Class ?: return emptySet()

        return classMetadata.kmClass.properties
            .filter { it.returnType.isNullable }
            .mapNotNull { it.getterSignature?.name }
            .toSet()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Annotation.read(method: String): T = annotationType().getMethod(method).invoke(this) as T
}
```

If `kotlin.metadata.jvm.Metadata(...)` does not resolve in `kotlin-metadata-jvm` 2.1.20, check the artifact's `JvmMetadataUtil` for the factory (it is the documented way to build a `kotlin.Metadata` instance) and adjust the import only — do not change behaviour.

- [ ] **Step 5: Wire the hook**

In `TypeModelBuilder`, replace:

```java
    private Set<String> kotlinNullableAccessors(Class<?> type) {
        return null;
    }
```

with:

```java
    private Set<String> kotlinNullableAccessors(Class<?> type) {
        return KotlinNullability.nullableAccessors(type);
    }
```

- [ ] **Step 6: Run all generator tests**

Run: `./gradlew :inertia4j.typescript:test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add inertia4j.typescript
git commit -m "Read Kotlin property nullability from class metadata"
```

---

### Task 11: Golden file and TypeScript validity check

**Files:**
- Create fixtures: `inertia4j.typescript/src/test/java/io/github/inertia4j/typescript/fixtures/sample/{Status,Album,Stat,User,AlbumsIndexProps,CreateAlbumForm,AppShared}.java`
- Create: `inertia4j.typescript/src/test/resources/expected/sample.d.ts`
- Test: `inertia4j.typescript/src/test/java/io/github/inertia4j/typescript/GoldenFileTest.java`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `TypeScriptGenerator.generate`, `GeneratorTestSupport`.

- [ ] **Step 1: Create sample fixtures**

```java
package io.github.inertia4j.typescript.fixtures.sample;

public enum Status { Active, Archived }
```

```java
package io.github.inertia4j.typescript.fixtures.sample;

public record Album(long id, String title, Status status) {}
```

```java
package io.github.inertia4j.typescript.fixtures.sample;

public record Stat(String label, double value) {}
```

```java
package io.github.inertia4j.typescript.fixtures.sample;

public record User(long id, String name) {}
```

```java
package io.github.inertia4j.typescript.fixtures.sample;

import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.core.DeferredProp;

import java.util.List;

@InertiaPage("Albums/Index")
public record AlbumsIndexProps(List<Album> albums, DeferredProp<List<Stat>> stats) {}
```

```java
package io.github.inertia4j.typescript.fixtures.sample;

import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public record CreateAlbumForm(String title, Status status) {}
```

```java
package io.github.inertia4j.typescript.fixtures.sample;

import io.github.inertia4j.annotations.InertiaShared;
import org.jspecify.annotations.Nullable;

@InertiaShared
public record AppShared(@Nullable User user, String appName) {}
```

- [ ] **Step 2: Write the expected file**

`inertia4j.typescript/src/test/resources/expected/sample.d.ts` (ends with a single newline):

```ts
// Generated by Inertia4J. Do not edit.
import '@inertiajs/core'

export interface Album {
  id: number
  title: string
  status: Status
}

export interface AlbumsIndexProps {
  albums: Album[]
  stats?: Stat[]
}

export interface AppShared {
  user: User | null
  appName: string
}

export interface CreateAlbumForm {
  title: string
  status: Status
}

export interface Stat {
  label: string
  value: number
}

export type Status = 'Active' | 'Archived'

export interface User {
  id: number
  name: string
}

export interface InertiaPages {
  'Albums/Index': AlbumsIndexProps
}

export type PageProps<C extends keyof InertiaPages> = InertiaPages[C]

declare module '@inertiajs/core' {
  export interface InertiaConfig {
    sharedPageProps: AppShared
    errorValueType: string
  }
}
```

- [ ] **Step 3: Write the tests**

`GoldenFileTest.java`:

```java
package io.github.inertia4j.typescript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static io.github.inertia4j.typescript.GeneratorTestSupport.generate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GoldenFileTest {
    @Test
    void sample_matchesGoldenFile() throws IOException {
        assertEquals(expected(), generate("sample").content());
    }

    @Test
    void sample_isValidTypeScript(@TempDir Path directory) throws Exception {
        assumeTrue(npxAvailable(), "npx not available, skipping tsc check");

        Files.writeString(directory.resolve("inertia.d.ts"), generate("sample").content());
        Path core = Files.createDirectories(directory.resolve("node_modules/@inertiajs/core"));
        Files.writeString(core.resolve("package.json"), "{\"name\":\"@inertiajs/core\",\"types\":\"index.d.ts\"}");
        Files.writeString(core.resolve("index.d.ts"), "export interface InertiaConfig {}\n");
        Files.writeString(directory.resolve("usage.ts"), """
            import type { PageProps, CreateAlbumForm } from './inertia'
            import type { InertiaConfig } from '@inertiajs/core'

            const index: PageProps<'Albums/Index'> = { albums: [{ id: 1, title: 'Blue', status: 'Active' }] }
            const form: CreateAlbumForm = { title: 'Blue', status: 'Archived' }
            const shared: InertiaConfig['sharedPageProps'] = { user: null, appName: 'Inertia4J' }
            const errors: InertiaConfig['errorValueType'] = 'required'

            export { index, form, shared, errors }
            """);

        Process process = new ProcessBuilder(
            "npx", "--yes", "-p", "typescript@5.6.3", "tsc",
            "--noEmit", "--strict", "--module", "esnext", "--moduleResolution", "node", "--target", "es2020",
            "inertia.d.ts", "usage.ts"
        ).directory(directory.toFile()).redirectErrorStream(true).start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assumeTrue(process.waitFor(3, TimeUnit.MINUTES), "tsc timed out");
        assertEquals(0, process.exitValue(), output);
    }

    private static String expected() throws IOException {
        try (InputStream in = GoldenFileTest.class.getResourceAsStream("/expected/sample.d.ts")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean npxAvailable() {
        try {
            Process process = new ProcessBuilder("npx", "--version").redirectErrorStream(true).start();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :inertia4j.typescript:test --tests '*GoldenFileTest'`
Expected: PASS for `sample_matchesGoldenFile`; `sample_isValidTypeScript` PASS when Node is installed (it is on the dev machine — verify it did not get skipped by checking `build/reports/tests/test/index.html` or the test XML for `skipped`).

If `sample_matchesGoldenFile` fails on ordering or spacing, fix the generator, never the golden file, unless the golden file contradicts the spec.

- [ ] **Step 5: Add Node to CI**

In `.github/workflows/ci.yml`, after the JDK step:

```yaml
      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
```

- [ ] **Step 6: Commit**

```bash
git add inertia4j.typescript .github/workflows/ci.yml
git commit -m "Add golden-file and tsc checks for generated TypeScript"
```

---

### Task 12: Gradle plugin

**Files:**
- Modify: `settings.gradle.kts`
- Create: `inertia4j.typescript-gradle-plugin/build.gradle.kts`
- Create: `inertia4j.typescript-gradle-plugin/src/main/java/io/github/inertia4j/typescript/gradle/InertiaTypesExtension.java`
- Create: `inertia4j.typescript-gradle-plugin/src/main/java/io/github/inertia4j/typescript/gradle/InertiaTypesTask.java`
- Create: `inertia4j.typescript-gradle-plugin/src/main/java/io/github/inertia4j/typescript/gradle/GenerateInertiaTypesTask.java`
- Create: `inertia4j.typescript-gradle-plugin/src/main/java/io/github/inertia4j/typescript/gradle/CheckInertiaTypesTask.java`
- Create: `inertia4j.typescript-gradle-plugin/src/main/java/io/github/inertia4j/typescript/gradle/InertiaTypesPlugin.java`
- Test: `inertia4j.typescript-gradle-plugin/src/test/java/io/github/inertia4j/typescript/gradle/InertiaTypesPluginTest.java`

**Interfaces:**
- Consumes: `TypeScriptGenerator.generate(GeneratorOptions, List<Path>)`, `GenerationResult`, `GenerationException`, `ErrorValueType`, `PropertyNaming`.
- Produces: plugin id `io.github.inertia4j.typescript`; extension `inertiaTypes` (`packages`, `outputFile`, `errorValueType`, `propertyNaming`, `nullableByDefault`); tasks `generateInertiaTypes`, `checkInertiaTypes`.

- [ ] **Step 1: Build setup**

Append to `settings.gradle.kts`:

```kotlin
include("inertia4j.typescript-gradle-plugin")
```

`inertia4j.typescript-gradle-plugin/build.gradle.kts`:

```kotlin
plugins {
    id("inertia4j.java-conventions")
    `java-gradle-plugin`
    `maven-publish`
}

version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(project(":inertia4j.typescript"))

    testImplementation(project(":inertia4j.typescript-annotations"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("inertiaTypes") {
            id = "io.github.inertia4j.typescript"
            implementationClass = "io.github.inertia4j.typescript.gradle.InertiaTypesPlugin"
            displayName = "Inertia4J TypeScript types"
            description = "Generates TypeScript types for Inertia4J props classes"
        }
    }
}
```

- [ ] **Step 2: Write the failing functional tests**

`InertiaTypesPluginTest.java`:

```java
package io.github.inertia4j.typescript.gradle;

import io.github.inertia4j.annotations.InertiaPage;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InertiaTypesPluginTest {
    @TempDir
    Path projectDir;

    @BeforeEach
    void setUp() throws Exception {
        String annotationsPath = Path.of(InertiaPage.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString()
            .replace('\\', '/');

        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"\n");
        writeBuildFile(annotationsPath, "listOf(\"com.example\")", "file(\"src/types/inertia.d.ts\")");
        writeUsersShow("String name");
    }

    @Test
    void generate_writesTypesFile() throws IOException {
        runner("generateInertiaTypes").build();

        String content = Files.readString(projectDir.resolve("src/types/inertia.d.ts"));
        assertTrue(content.contains("'Users/Show': UsersShow"), content);
        assertTrue(content.contains("  name: string\n"), content);
    }

    @Test
    void generate_isUpToDateOnSecondRun() {
        runner("generateInertiaTypes").build();

        BuildResult second = runner("generateInertiaTypes").build();

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateInertiaTypes").getOutcome());
    }

    @Test
    void check_passesWhenTypesAreCurrent() {
        runner("generateInertiaTypes").build();

        BuildResult result = runner("checkInertiaTypes").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkInertiaTypes").getOutcome());
    }

    @Test
    void check_failsWhenTypesAreStale() throws IOException {
        runner("generateInertiaTypes").build();
        writeUsersShow("String name, int age");

        BuildResult result = runner("checkInertiaTypes").buildAndFail();

        assertTrue(result.getOutput().contains("Inertia types are out of date, run `generateInertiaTypes`"), result.getOutput());
    }

    @Test
    void generate_withoutPackages_fails() throws Exception {
        String annotationsPath = Path.of(InertiaPage.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString()
            .replace('\\', '/');
        writeBuildFile(annotationsPath, "listOf()", "file(\"src/types/inertia.d.ts\")");

        BuildResult result = runner("generateInertiaTypes").buildAndFail();

        assertTrue(result.getOutput().contains("inertiaTypes.packages"), result.getOutput());
    }

    private GradleRunner runner(String task) {
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(task, "--stacktrace");
    }

    private void writeBuildFile(String annotationsPath, String packages, String outputFile) throws IOException {
        Files.writeString(projectDir.resolve("build.gradle.kts"), """
            plugins {
                java
                id("io.github.inertia4j.typescript")
            }

            dependencies {
                implementation(files("%s"))
            }

            inertiaTypes {
                packages.set(%s)
                outputFile.set(%s)
            }
            """.formatted(annotationsPath, packages, outputFile));
    }

    private void writeUsersShow(String components) throws IOException {
        Path source = projectDir.resolve("src/main/java/com/example/UsersShow.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.example;

            import io.github.inertia4j.annotations.InertiaPage;

            @InertiaPage("Users/Show")
            public record UsersShow(%s) {}
            """.formatted(components));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :inertia4j.typescript-gradle-plugin:test`
Expected: FAIL — plugin implementation class `InertiaTypesPlugin` not found (build of the plugin module fails at `pluginDescriptors` validation or compilation).

- [ ] **Step 4: Implement the extension**

```java
package io.github.inertia4j.typescript.gradle;

import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.typescript.ErrorValueType;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration of the {@code inertiaTypes} extension.
 */
public abstract class InertiaTypesExtension {
    /**
     * @return package roots holding the props classes. Required.
     */
    public abstract ListProperty<String> getPackages();

    /**
     * @return generated file. Defaults to {@code build/inertia/inertia.d.ts}.
     */
    public abstract RegularFileProperty getOutputFile();

    /**
     * @return type of validation error values. Defaults to {@link ErrorValueType#String}.
     */
    public abstract Property<ErrorValueType> getErrorValueType();

    /**
     * @return naming strategy of the JSON serializer. Defaults to {@link PropertyNaming#Camel}.
     */
    public abstract Property<PropertyNaming> getPropertyNaming();

    /**
     * @return whether unannotated Java properties are nullable. Defaults to {@code false}.
     */
    public abstract Property<Boolean> getNullableByDefault();
}
```

- [ ] **Step 5: Implement the tasks**

`InertiaTypesTask.java`:

```java
package io.github.inertia4j.typescript.gradle;

import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.typescript.ErrorValueType;
import io.github.inertia4j.typescript.GenerationException;
import io.github.inertia4j.typescript.GenerationResult;
import io.github.inertia4j.typescript.GeneratorOptions;
import io.github.inertia4j.typescript.TypeScriptGenerator;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Base class of the Inertia types tasks: holds the generator inputs and runs the generator.
 */
public abstract class InertiaTypesTask extends DefaultTask {
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract ListProperty<String> getPackages();

    @Input
    public abstract Property<ErrorValueType> getErrorValueType();

    @Input
    public abstract Property<PropertyNaming> getPropertyNaming();

    @Input
    public abstract Property<Boolean> getNullableByDefault();

    protected String generateContent() {
        List<String> packages = getPackages().get();
        if (packages.isEmpty()) {
            throw new InvalidUserDataException("inertiaTypes.packages must list at least one package holding your props classes");
        }

        GeneratorOptions options = new GeneratorOptions(
            packages,
            getPropertyNaming().get(),
            getErrorValueType().get(),
            getNullableByDefault().get()
        );
        List<Path> classpath = getClasspath().getFiles().stream().map(File::toPath).toList();

        try {
            GenerationResult result = TypeScriptGenerator.generate(options, classpath);
            result.warnings().forEach(warning -> getLogger().warn("Inertia types: {}", warning));

            return result.content();
        } catch (GenerationException e) {
            throw new GradleException("Inertia types generation failed: " + e.getMessage(), e);
        }
    }
}
```

`GenerateInertiaTypesTask.java`:

```java
package io.github.inertia4j.typescript.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the TypeScript declaration file for the Inertia props classes.
 */
@CacheableTask
public abstract class GenerateInertiaTypesTask extends InertiaTypesTask {
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() throws IOException {
        Path outputFile = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, generateContent());
    }
}
```

`CheckInertiaTypesTask.java`:

```java
package io.github.inertia4j.typescript.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fails when the committed TypeScript declaration file differs from freshly generated types.
 */
public abstract class CheckInertiaTypesTask extends InertiaTypesTask {
    public CheckInertiaTypesTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    @Internal
    public abstract RegularFileProperty getTypesFile();

    @TaskAction
    public void check() throws IOException {
        Path typesFile = getTypesFile().get().getAsFile().toPath();
        String expected = generateContent();

        if (!Files.exists(typesFile) || !Files.readString(typesFile).equals(expected)) {
            throw new GradleException("Inertia types are out of date, run `generateInertiaTypes`");
        }
    }
}
```

- [ ] **Step 6: Implement the plugin**

```java
package io.github.inertia4j.typescript.gradle;

import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.typescript.ErrorValueType;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Adds the {@code inertiaTypes} extension and the {@code generateInertiaTypes} / {@code checkInertiaTypes} tasks
 * to projects applying the {@code java} plugin.
 */
public class InertiaTypesPlugin implements Plugin<Project> {
    private static final String TaskGroup = "inertia";

    @Override
    public void apply(Project project) {
        InertiaTypesExtension extension = project.getExtensions().create("inertiaTypes", InertiaTypesExtension.class);
        extension.getOutputFile().convention(project.getLayout().getBuildDirectory().file("inertia/inertia.d.ts"));
        extension.getErrorValueType().convention(ErrorValueType.String);
        extension.getPropertyNaming().convention(PropertyNaming.Camel);
        extension.getNullableByDefault().convention(false);

        project.getPluginManager().withPlugin("java", plugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            FileCollection runtimeClasspath = sourceSets.getByName("main").getRuntimeClasspath();

            project.getTasks().register("generateInertiaTypes", GenerateInertiaTypesTask.class, task -> {
                configure(task, extension, runtimeClasspath);
                task.setDescription("Generates TypeScript types for Inertia props classes.");
                task.getOutputFile().set(extension.getOutputFile());
            });

            project.getTasks().register("checkInertiaTypes", CheckInertiaTypesTask.class, task -> {
                configure(task, extension, runtimeClasspath);
                task.setDescription("Checks that the generated Inertia TypeScript types are up to date.");
                task.getTypesFile().set(extension.getOutputFile());
            });
        });
    }

    private static void configure(InertiaTypesTask task, InertiaTypesExtension extension, FileCollection runtimeClasspath) {
        task.setGroup(TaskGroup);
        task.getClasspath().from(runtimeClasspath);
        task.getPackages().set(extension.getPackages());
        task.getErrorValueType().set(extension.getErrorValueType());
        task.getPropertyNaming().set(extension.getPropertyNaming());
        task.getNullableByDefault().set(extension.getNullableByDefault());
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :inertia4j.typescript-gradle-plugin:test`
Expected: PASS (5 functional tests). TestKit runs are slow (~1 min).

- [ ] **Step 8: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (tests, javadoc, plugin descriptor validation).

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts inertia4j.typescript-gradle-plugin
git commit -m "Add Gradle plugin generating and checking Inertia TypeScript types"
```

---

### Task 13: Documentation

**Files:**
- Create: `docs/typescript.md`
- Modify: `README.md`, `inertia4j.spring-boot-3/README.md`, `inertia4j.spring-boot-4/README.md`, `inertia4j.ktor/README.md`, `docs/roadmap.md`

- [ ] **Step 1: Write `docs/typescript.md`**

```markdown
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
`propertyNaming = PropertyNaming.Snake` (Ktor), and configure your JSON serializer the same way.

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
```

- [ ] **Step 2: Link it**

In `README.md`, add after the Ktor bullet:

```markdown
* [Generating TypeScript types for props](/docs/typescript.md)
```

In each adapter README (`inertia4j.spring-boot-3/README.md`, `inertia4j.spring-boot-4/README.md`, `inertia4j.ktor/README.md`), append:

```markdown
### Typed props

Props can be described by classes annotated with `@InertiaPage`, `@InertiaShared` and `@InertiaForm`, rendered with
`inertia.render(pageProps)`, and turned into TypeScript types by the `io.github.inertia4j.typescript` Gradle plugin.
See [TypeScript types](/docs/typescript.md).
```

In `docs/roadmap.md`, change `- [ ] Generate TypeScript types for props` to `- [x] Generate TypeScript types for props`.

- [ ] **Step 3: Verify the build still passes**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add docs README.md inertia4j.spring-boot-3/README.md inertia4j.spring-boot-4/README.md inertia4j.ktor/README.md
git commit -m "Document TypeScript type generation"
```
