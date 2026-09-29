# Vite Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render Vite dev-server or manifest-based `<script>`/`<link>` tags into the Inertia root template, derive the
Inertia asset version from the Vite manifest, serve built assets from Spring and Ktor, and ship a runnable Spring Boot +
React example.

**Architecture:** A zero-dependency `io.github.inertia4j.core.vite` package in `inertia4j.core` (manifest model, small
JSON reader, `Vite` facade producing tags and the version) is consumed by `SimpleTemplateRenderer` through
`@Vite(...)@` / `@ViteReactRefresh@` placeholders. Spring (shared by Boot 3 and 4) and Ktor wire a `Vite` instance from
their configuration, use it as default version provider and template renderer input, and register a static resource
route for the build directory. Dev mode is detected by a `vite.hot` file written by an inline Vite plugin.

**Tech Stack:** Java 11 (core, Ktor), Java 17 (Spring modules), Kotlin 2.1 / Ktor 3.0, Spring Boot 3.3 and 4.0, JUnit
5, Gradle Kotlin DSL; example app: Vite, React, TypeScript, `@inertiajs/react` 2.x.

**Spec:** `docs/superpowers/specs/2026-09-29-vite-integration-design.md`

## Global Constraints

- Work in the worktree `/Users/aritti/Projects/inertia4j/.claude/worktrees/vite-integration`; run every command from its root.
- `inertia4j.core` and `inertia4j.ktor` target Java 11: no records, no `HexFormat`, no switch expressions. Spring modules target Java 17.
- `inertia4j.core` gains no runtime dependency (Jackson stays `compileOnly`).
- New core classes live in `io.github.inertia4j.core.vite` and are annotated `@NullMarked` (jspecify, available through `inertia4j.spi`).
- Defaults: hot file `vite.hot`; build directory `static/build`; manifest `{buildDirectory}/.vite/manifest.json`; public path `/build/`; cache max-age `365d`.
- Version values: lowercase hex SHA-256 of the manifest bytes in production, `"dev"` in dev mode, `"1"` when neither hot file nor manifest is usable.
- `TemplateRenderer` SPI and existing public constructors stay source-compatible.
- `ViteException` extends `io.github.inertia4j.spi.InertiaException` (unchecked).
- Example app uses `@inertiajs/react` 2.x (the protocol implemented on `main` is Inertia 2).
- Test naming: Java `method_whenCondition_expectedResult`; Kotlin backtick sentences. No comments in tests.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

## Review Focus

- React preamble contains `$RefreshReg$`: placeholder substitution must quote replacements, or `Matcher.appendReplacement` throws / drops text — test in Task 6.
- Hot file written with trailing newline, trailing `/`, or blank while Vite is mid-write: URL must be trimmed, blank means production — tests in Task 5.
- Template entry written with a leading slash (`@Vite(/src/main.tsx)@`): must resolve the manifest key `src/main.tsx` — test in Task 4.
- Concurrent full-page renders: today's `SimpleTemplateRenderer` shares one `Matcher` across threads; renders must be independent — test in Task 6.
- Circular chunk imports in the manifest (possible with manual chunks): tag collection must terminate — test in Task 4.

## File Structure

| File | Responsibility |
|---|---|
| `inertia4j.core/src/main/java/io/github/inertia4j/core/vite/ViteException.java` | Error type for all Vite failures |
| `.../core/vite/ManifestJsonReader.java` | Package-private JSON parser |
| `.../core/vite/ManifestChunk.java` | One manifest chunk |
| `.../core/vite/ViteManifest.java` | Parsed manifest, entry lookup |
| `.../core/vite/ViteConfig.java` | Immutable settings + builder, path normalisation |
| `.../core/vite/Vite.java` | Facade: dev detection, tags, React preamble, version |
| `.../core/vite/ViteVersionProvider.java` | `Supplier<String>` adapter |
| `inertia4j.core/src/main/java/io/github/inertia4j/core/SimpleTemplateRenderer.java` | Placeholder substitution |
| `inertia4j.spring-shared/.../InertiaConfigurationProperties.java` | Constructor-bound `inertia.*` + `inertia.vite.*` |
| `inertia4j.spring-shared/.../AbstractInertiaSpringAutoconfiguration.java` | `Vite`, version, renderer, asset handler beans |
| `inertia4j.ktor/src/main/kotlin/ViteKtorConfiguration.kt` | Ktor `vite { }` block |
| `inertia4j.ktor/src/main/kotlin/InertiaKtorConfiguration.kt`, `InertiaPlugin.kt` | Wiring + static route |
| `examples/spring-boot-react/**` | Standalone example build |
| `docs/vite.md` | User guide |

---

### Task 1: Manifest JSON reader

**Files:**
- Create: `inertia4j.core/src/main/java/io/github/inertia4j/core/vite/ViteException.java`
- Create: `inertia4j.core/src/main/java/io/github/inertia4j/core/vite/ManifestJsonReader.java`
- Test: `inertia4j.core/src/test/java/io/github/inertia4j/core/vite/ManifestJsonReaderTest.java`

**Interfaces:**
- Consumes: `io.github.inertia4j.spi.InertiaException(String)`, `(String, Throwable)`.
- Produces: `public class ViteException extends InertiaException` with constructors `(String message)` and `(String message, Throwable cause)`; package-private `static @Nullable Object ManifestJsonReader.read(String json)` returning `Map<String, @Nullable Object>` (`LinkedHashMap`), `List<@Nullable Object>`, `String`, `Boolean`, `Double` or `null`. Errors: `ViteException("Invalid Vite manifest: <reason> at offset <n>")`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.inertia4j.core.vite;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestJsonReaderTest {
    @Test
    void read_whenObjectHasEveryValueType_returnsNestedMapsAndLists() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("k", "v");
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("s", "text");
        expected.put("t", true);
        expected.put("f", false);
        expected.put("n", null);
        expected.put("d", -150.0);
        expected.put("a", Arrays.asList(1.0, "x"));
        expected.put("o", nested);

        Object result = ManifestJsonReader.read(
            "{\"s\":\"text\",\"t\":true,\"f\":false,\"n\":null,\"d\":-1.5e2,\"a\":[1,\"x\"],\"o\":{\"k\":\"v\"}}"
        );

        assertEquals(expected, result);
    }

    @Test
    void read_whenObjectHasKeys_preservesTheirOrder() {
        Object result = ManifestJsonReader.read("{\"b\":1,\"a\":2,\"c\":3}");

        assertEquals(List.of("b", "a", "c"), new ArrayList<>(((Map<?, ?>) result).keySet()));
    }

    @Test
    void read_whenStringHasEscapes_decodesThem() {
        Object result = ManifestJsonReader.read("\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\\u00e9\"");

        assertEquals("a\"b\\c/d\b\f\n\r\t\u00e9", result);
    }

    @Test
    void read_whenWhitespaceSurroundsTokens_ignoresIt() {
        Object result = ManifestJsonReader.read(" \n{ \"a\" : [ ] , \"b\" : { } }\t");

        assertEquals(Map.of("a", List.of(), "b", Map.of()), result);
    }

    @Test
    void read_whenTrailingContent_throwsWithOffset() {
        ViteException exception = assertThrows(ViteException.class, () -> ManifestJsonReader.read("{} x"));

        assertEquals("Invalid Vite manifest: unexpected trailing content at offset 3", exception.getMessage());
    }

    @Test
    void read_whenColonMissing_throwsWithOffset() {
        ViteException exception = assertThrows(ViteException.class, () -> ManifestJsonReader.read("{\"a\" 1}"));

        assertEquals("Invalid Vite manifest: expected ':' at offset 5", exception.getMessage());
    }

    @Test
    void read_whenInputEndsEarly_throwsWithOffset() {
        ViteException exception = assertThrows(ViteException.class, () -> ManifestJsonReader.read("{\"a\":"));

        assertEquals("Invalid Vite manifest: unexpected end of input at offset 5", exception.getMessage());
    }

    @Test
    void read_whenEscapeIsInvalid_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> ManifestJsonReader.read("\"\\x\""));

        assertTrue(exception.getMessage().startsWith("Invalid Vite manifest: invalid escape"));
    }

    @Test
    void read_whenLiteralIsMisspelled_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> ManifestJsonReader.read("[tru]"));

        assertEquals("Invalid Vite manifest: unexpected character 't' at offset 1", exception.getMessage());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :inertia4j.core:test --tests 'io.github.inertia4j.core.vite.ManifestJsonReaderTest'`
Expected: FAIL — compilation error, `ManifestJsonReader` / `ViteException` not found.

- [ ] **Step 3: Write minimal implementation**

`ViteException.java`:

```java
package io.github.inertia4j.core.vite;

import io.github.inertia4j.spi.InertiaException;

/**
 * Exception thrown when Vite tags cannot be produced, for example when the manifest is missing or invalid.
 */
public class ViteException extends InertiaException {
    /**
     * @param message description of the failure.
     */
    public ViteException(String message) {
        super(message);
    }

    /**
     * @param message description of the failure.
     * @param cause underlying error.
     */
    public ViteException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`ManifestJsonReader.java`:

```java
package io.github.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser for the Vite manifest, so that the core module needs no JSON library at runtime.
 */
@NullMarked
class ManifestJsonReader {
    private final String json;
    private int position;

    private ManifestJsonReader(String json) {
        this.json = json;
    }

    static @Nullable Object read(String json) {
        ManifestJsonReader reader = new ManifestJsonReader(json);
        reader.skipWhitespace();
        Object value = reader.readValue();
        reader.skipWhitespace();

        if (reader.position != json.length()) {
            throw reader.error("unexpected trailing content");
        }

        return value;
    }

    private @Nullable Object readValue() {
        char current = peek();

        if (current == '{') {
            return readObject();
        }

        if (current == '[') {
            return readArray();
        }

        if (current == '"') {
            return readString();
        }

        if (current == 't') {
            readLiteral("true");
            return Boolean.TRUE;
        }

        if (current == 'f') {
            readLiteral("false");
            return Boolean.FALSE;
        }

        if (current == 'n') {
            readLiteral("null");
            return null;
        }

        if (current == '-' || (current >= '0' && current <= '9')) {
            return readNumber();
        }

        throw error("unexpected character '" + current + "'");
    }

    private Map<String, @Nullable Object> readObject() {
        Map<String, @Nullable Object> object = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();

        if (peek() == '}') {
            position++;
            return object;
        }

        while (true) {
            skipWhitespace();

            if (peek() != '"') {
                throw error("expected string key");
            }

            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, readValue());
            skipWhitespace();

            if (peek() == ',') {
                position++;
                continue;
            }

            expect('}');
            return object;
        }
    }

    private List<@Nullable Object> readArray() {
        List<@Nullable Object> array = new ArrayList<>();
        expect('[');
        skipWhitespace();

        if (peek() == ']') {
            position++;
            return array;
        }

        while (true) {
            skipWhitespace();
            array.add(readValue());
            skipWhitespace();

            if (peek() == ',') {
                position++;
                continue;
            }

            expect(']');
            return array;
        }
    }

    private String readString() {
        expect('"');
        StringBuilder builder = new StringBuilder();

        while (true) {
            char current = next();

            if (current == '"') {
                return builder.toString();
            }

            if (current != '\\') {
                builder.append(current);
                continue;
            }

            builder.append(readEscape());
        }
    }

    private char readEscape() {
        char escaped = next();

        switch (escaped) {
            case '"':
                return '"';
            case '\\':
                return '\\';
            case '/':
                return '/';
            case 'b':
                return '\b';
            case 'f':
                return '\f';
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'u':
                return readUnicodeEscape();
            default:
                throw error("invalid escape '\\" + escaped + "'");
        }
    }

    private char readUnicodeEscape() {
        if (position + 4 > json.length()) {
            throw error("invalid unicode escape");
        }

        String hex = json.substring(position, position + 4);

        try {
            char decoded = (char) Integer.parseInt(hex, 16);
            position += 4;
            return decoded;
        } catch (NumberFormatException e) {
            throw error("invalid unicode escape");
        }
    }

    private Double readNumber() {
        int start = position;

        while (position < json.length() && "+-0123456789.eE".indexOf(json.charAt(position)) >= 0) {
            position++;
        }

        try {
            return Double.valueOf(json.substring(start, position));
        } catch (NumberFormatException e) {
            position = start;
            throw error("invalid number");
        }
    }

    private void readLiteral(String literal) {
        if (!json.startsWith(literal, position)) {
            throw error("unexpected character '" + json.charAt(position) + "'");
        }

        position += literal.length();
    }

    private void expect(char expected) {
        if (peek() != expected) {
            throw error("expected '" + expected + "'");
        }

        position++;
    }

    private char peek() {
        if (position >= json.length()) {
            throw error("unexpected end of input");
        }

        return json.charAt(position);
    }

    private char next() {
        char current = peek();
        position++;
        return current;
    }

    private void skipWhitespace() {
        while (position < json.length() && Character.isWhitespace(json.charAt(position))) {
            position++;
        }
    }

    private ViteException error(String reason) {
        return new ViteException("Invalid Vite manifest: " + reason + " at offset " + position);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :inertia4j.core:test --tests 'io.github.inertia4j.core.vite.ManifestJsonReaderTest'`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add inertia4j.core/src/main/java/io/github/inertia4j/core/vite inertia4j.core/src/test/java/io/github/inertia4j/core/vite
git commit -m "Add dependency-free JSON reader for the Vite manifest

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Manifest model

**Files:**
- Create: `inertia4j.core/src/main/java/io/github/inertia4j/core/vite/ManifestChunk.java`
- Create: `inertia4j.core/src/main/java/io/github/inertia4j/core/vite/ViteManifest.java`
- Test: `inertia4j.core/src/test/java/io/github/inertia4j/core/vite/ViteManifestTest.java`

**Interfaces:**
- Consumes: `ManifestJsonReader.read(String)`, `ViteException(String)` (Task 1).
- Produces:
  - `ManifestChunk` getters: `String getFile()`, `@Nullable String getSrc()`, `@Nullable String getName()`, `boolean isEntry()`, `boolean isDynamicEntry()`, `List<String> getImports()`, `List<String> getDynamicImports()`, `List<String> getCss()`, `List<String> getAssets()`.
  - `public static ViteManifest ViteManifest.parse(String json)`, `Optional<ManifestChunk> chunk(String key)`, `Set<String> entries()` (manifest order).

- [ ] **Step 1: Write the failing test**

```java
package io.github.inertia4j.core.vite;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteManifestTest {
    @Test
    void parse_whenChunkHasEveryField_mapsThem() {
        ViteManifest manifest = ViteManifest.parse(
            "{\"views/foo.js\":{\"file\":\"assets/foo.js\",\"name\":\"foo\",\"src\":\"views/foo.js\","
                + "\"isEntry\":true,\"isDynamicEntry\":true,\"imports\":[\"_shared.js\"],"
                + "\"dynamicImports\":[\"baz.js\"],\"css\":[\"assets/foo.css\"],\"assets\":[\"assets/logo.svg\"]}}"
        );

        ManifestChunk chunk = manifest.chunk("views/foo.js").orElseThrow();

        assertEquals("assets/foo.js", chunk.getFile());
        assertEquals("foo", chunk.getName());
        assertEquals("views/foo.js", chunk.getSrc());
        assertTrue(chunk.isEntry());
        assertTrue(chunk.isDynamicEntry());
        assertEquals(List.of("_shared.js"), chunk.getImports());
        assertEquals(List.of("baz.js"), chunk.getDynamicImports());
        assertEquals(List.of("assets/foo.css"), chunk.getCss());
        assertEquals(List.of("assets/logo.svg"), chunk.getAssets());
    }

    @Test
    void parse_whenOptionalFieldsAreAbsent_usesDefaults() {
        ManifestChunk chunk = ViteManifest.parse("{\"_shared.js\":{\"file\":\"assets/shared.js\"}}")
            .chunk("_shared.js")
            .orElseThrow();

        assertNull(chunk.getSrc());
        assertNull(chunk.getName());
        assertFalse(chunk.isEntry());
        assertFalse(chunk.isDynamicEntry());
        assertEquals(List.of(), chunk.getImports());
        assertEquals(List.of(), chunk.getDynamicImports());
        assertEquals(List.of(), chunk.getCss());
        assertEquals(List.of(), chunk.getAssets());
    }

    @Test
    void entries_returnsKeysOfEntryChunksInManifestOrder() {
        ViteManifest manifest = ViteManifest.parse(
            "{\"b.js\":{\"file\":\"b.js\",\"isEntry\":true},\"_a.js\":{\"file\":\"a.js\"},\"c.js\":{\"file\":\"c.js\",\"isEntry\":true}}"
        );

        assertEquals(List.of("b.js", "c.js"), new ArrayList<>(manifest.entries()));
    }

    @Test
    void chunk_whenKeyIsUnknown_returnsEmpty() {
        assertTrue(ViteManifest.parse("{}").chunk("missing.js").isEmpty());
    }

    @Test
    void parse_whenRootIsNotAnObject_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> ViteManifest.parse("[]"));

        assertEquals("Invalid Vite manifest: expected a JSON object", exception.getMessage());
    }

    @Test
    void parse_whenChunkIsNotAnObject_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> ViteManifest.parse("{\"a\":1}"));

        assertEquals("Invalid Vite manifest: chunk 'a' is not a JSON object", exception.getMessage());
    }

    @Test
    void parse_whenChunkHasNoFile_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> ViteManifest.parse("{\"a\":{}}"));

        assertEquals("Invalid Vite manifest: chunk 'a' has no file", exception.getMessage());
    }

    @Test
    void parse_whenListHoldsNonString_throws() {
        ViteException exception = assertThrows(
            ViteException.class,
            () -> ViteManifest.parse("{\"a\":{\"file\":\"a.js\",\"css\":[1]}}")
        );

        assertEquals("Invalid Vite manifest: chunk 'a' has a non-string value in 'css'", exception.getMessage());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :inertia4j.core:test --tests 'io.github.inertia4j.core.vite.ViteManifestTest'`
Expected: FAIL — compilation error, `ViteManifest` not found.

- [ ] **Step 3: Write minimal implementation**

`ManifestChunk.java`:

```java
package io.github.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A chunk of the Vite manifest, mirroring Vite's {@code ManifestChunk} interface.
 *
 * @see <a href="https://vite.dev/guide/backend-integration.html">Vite backend integration</a>
 */
@NullMarked
public class ManifestChunk {
    private final String file;
    private final @Nullable String src;
    private final @Nullable String name;
    private final boolean entry;
    private final boolean dynamicEntry;
    private final List<String> imports;
    private final List<String> dynamicImports;
    private final List<String> css;
    private final List<String> assets;

    ManifestChunk(String key, Map<?, ?> json) {
        Object file = json.get("file");

        if (!(file instanceof String)) {
            throw new ViteException("Invalid Vite manifest: chunk '" + key + "' has no file");
        }

        this.file = (String) file;
        this.src = optionalString(key, json, "src");
        this.name = optionalString(key, json, "name");
        this.entry = Boolean.TRUE.equals(json.get("isEntry"));
        this.dynamicEntry = Boolean.TRUE.equals(json.get("isDynamicEntry"));
        this.imports = stringList(key, json, "imports");
        this.dynamicImports = stringList(key, json, "dynamicImports");
        this.css = stringList(key, json, "css");
        this.assets = stringList(key, json, "assets");
    }

    /**
     * @return output file, relative to the build directory.
     */
    public String getFile() {
        return file;
    }

    /**
     * @return input file, relative to the Vite root, if any.
     */
    public @Nullable String getSrc() {
        return src;
    }

    /**
     * @return chunk name, if any.
     */
    public @Nullable String getName() {
        return name;
    }

    /**
     * @return whether the chunk is an entry point.
     */
    public boolean isEntry() {
        return entry;
    }

    /**
     * @return whether the chunk is loaded through a dynamic import.
     */
    public boolean isDynamicEntry() {
        return dynamicEntry;
    }

    /**
     * @return manifest keys of statically imported chunks.
     */
    public List<String> getImports() {
        return imports;
    }

    /**
     * @return manifest keys of dynamically imported chunks.
     */
    public List<String> getDynamicImports() {
        return dynamicImports;
    }

    /**
     * @return stylesheets of the chunk, relative to the build directory.
     */
    public List<String> getCss() {
        return css;
    }

    /**
     * @return assets of the chunk, relative to the build directory.
     */
    public List<String> getAssets() {
        return assets;
    }

    private static @Nullable String optionalString(String key, Map<?, ?> json, String field) {
        Object value = json.get(field);

        if (value == null) {
            return null;
        }

        if (!(value instanceof String)) {
            throw new ViteException("Invalid Vite manifest: chunk '" + key + "' has a non-string '" + field + "'");
        }

        return (String) value;
    }

    private static List<String> stringList(String key, Map<?, ?> json, String field) {
        Object value = json.get(field);

        if (value == null) {
            return List.of();
        }

        if (!(value instanceof List)) {
            throw new ViteException("Invalid Vite manifest: chunk '" + key + "' has a non-array '" + field + "'");
        }

        List<String> strings = new ArrayList<>();

        for (Object item : (List<?>) value) {
            if (!(item instanceof String)) {
                throw new ViteException(
                    "Invalid Vite manifest: chunk '" + key + "' has a non-string value in '" + field + "'"
                );
            }

            strings.add((String) item);
        }

        return Collections.unmodifiableList(strings);
    }
}
```

`ViteManifest.java`:

```java
package io.github.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parsed {@code .vite/manifest.json}, mapping manifest keys to chunks.
 */
@NullMarked
public class ViteManifest {
    private final Map<String, ManifestChunk> chunks;

    private ViteManifest(Map<String, ManifestChunk> chunks) {
        this.chunks = chunks;
    }

    /**
     * Parses the content of a Vite manifest.
     *
     * @param json manifest content.
     * @return parsed manifest.
     * @throws ViteException if the content is not a valid manifest.
     */
    public static ViteManifest parse(String json) {
        Object root = ManifestJsonReader.read(json);

        if (!(root instanceof Map)) {
            throw new ViteException("Invalid Vite manifest: expected a JSON object");
        }

        Map<String, ManifestChunk> chunks = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : ((Map<?, ?>) root).entrySet()) {
            String key = (String) entry.getKey();

            if (!(entry.getValue() instanceof Map)) {
                throw new ViteException("Invalid Vite manifest: chunk '" + key + "' is not a JSON object");
            }

            chunks.put(key, new ManifestChunk(key, (Map<?, ?>) entry.getValue()));
        }

        return new ViteManifest(Collections.unmodifiableMap(chunks));
    }

    /**
     * @param key manifest key, such as {@code src/main.tsx}.
     * @return the chunk, or empty if the manifest has no such key.
     */
    public Optional<ManifestChunk> chunk(String key) {
        return Optional.ofNullable(chunks.get(key));
    }

    /**
     * @return keys of entry chunks, in manifest order.
     */
    public Set<String> entries() {
        Set<String> entries = new LinkedHashSet<>();

        chunks.forEach((key, chunk) -> {
            if (chunk.isEntry()) {
                entries.add(key);
            }
        });

        return Collections.unmodifiableSet(entries);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :inertia4j.core:test --tests 'io.github.inertia4j.core.vite.ViteManifestTest'`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add inertia4j.core/src/main/java/io/github/inertia4j/core/vite inertia4j.core/src/test/java/io/github/inertia4j/core/vite
git commit -m "Add Vite manifest model

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Vite configuration

**Files:**
- Create: `inertia4j.core/src/main/java/io/github/inertia4j/core/vite/ViteConfig.java`
- Test: `inertia4j.core/src/test/java/io/github/inertia4j/core/vite/ViteConfigTest.java`

**Interfaces:**
- Produces: `ViteConfig.builder()` → `ViteConfig.Builder` with `hotFile(Path)`, `buildDirectory(String)`, `manifestPath(@Nullable String)`, `publicPath(String)`, `build()`; `ViteConfig.defaults()`; getters `Path getHotFile()`, `String getBuildDirectory()` (no leading/trailing `/`), `String getManifestPath()` (no leading `/`), `String getPublicPath()` (starts and ends with `/`).

- [ ] **Step 1: Write the failing test**

```java
package io.github.inertia4j.core.vite;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViteConfigTest {
    @Test
    void defaults_matchDocumentedViteConfig() {
        ViteConfig config = ViteConfig.defaults();

        assertEquals(Path.of("vite.hot"), config.getHotFile());
        assertEquals("static/build", config.getBuildDirectory());
        assertEquals("static/build/.vite/manifest.json", config.getManifestPath());
        assertEquals("/build/", config.getPublicPath());
    }

    @Test
    void build_whenBuildDirectoryChanges_derivesManifestPath() {
        ViteConfig config = ViteConfig.builder().buildDirectory("public/dist").build();

        assertEquals("public/dist/.vite/manifest.json", config.getManifestPath());
    }

    @Test
    void build_whenManifestPathIsSet_usesIt() {
        ViteConfig config = ViteConfig.builder().manifestPath("/custom/manifest.json").build();

        assertEquals("custom/manifest.json", config.getManifestPath());
    }

    @Test
    void build_whenManifestPathIsNull_derivesIt() {
        ViteConfig config = ViteConfig.builder().manifestPath(null).build();

        assertEquals("static/build/.vite/manifest.json", config.getManifestPath());
    }

    @Test
    void build_stripsSlashesAroundBuildDirectory() {
        assertEquals("static/build", ViteConfig.builder().buildDirectory("/static/build/").build().getBuildDirectory());
    }

    @Test
    void build_normalisesPublicPath() {
        assertEquals("/build/", ViteConfig.builder().publicPath("build").build().getPublicPath());
        assertEquals("/build/", ViteConfig.builder().publicPath("/build").build().getPublicPath());
        assertEquals("/", ViteConfig.builder().publicPath("").build().getPublicPath());
        assertEquals("/", ViteConfig.builder().publicPath("/").build().getPublicPath());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :inertia4j.core:test --tests 'io.github.inertia4j.core.vite.ViteConfigTest'`
Expected: FAIL — compilation error, `ViteConfig` not found.

- [ ] **Step 3: Write minimal implementation**

```java
package io.github.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Settings of the Vite integration. Defaults match the {@code vite.config.ts} documented in {@code docs/vite.md}.
 */
@NullMarked
public class ViteConfig {
    private final Path hotFile;
    private final String buildDirectory;
    private final String manifestPath;
    private final String publicPath;

    private ViteConfig(Path hotFile, String buildDirectory, String manifestPath, String publicPath) {
        this.hotFile = hotFile;
        this.buildDirectory = buildDirectory;
        this.manifestPath = manifestPath;
        this.publicPath = publicPath;
    }

    /**
     * @return a builder initialised with the defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the default configuration.
     */
    public static ViteConfig defaults() {
        return builder().build();
    }

    /**
     * @return file whose presence, written by the Vite dev server, enables dev mode.
     */
    public Path getHotFile() {
        return hotFile;
    }

    /**
     * @return classpath directory containing the build output, without leading or trailing slash.
     */
    public String getBuildDirectory() {
        return buildDirectory;
    }

    /**
     * @return classpath location of the manifest, without leading slash.
     */
    public String getManifestPath() {
        return manifestPath;
    }

    /**
     * @return URL prefix of built files, starting and ending with a slash.
     */
    public String getPublicPath() {
        return publicPath;
    }

    /**
     * Builder of {@link ViteConfig}.
     */
    public static class Builder {
        private Path hotFile = Path.of("vite.hot");
        private String buildDirectory = "static/build";
        private @Nullable String manifestPath;
        private String publicPath = "/build/";

        private Builder() {
        }

        /**
         * @param hotFile file whose presence enables dev mode.
         * @return this builder.
         */
        public Builder hotFile(Path hotFile) {
            this.hotFile = hotFile;
            return this;
        }

        /**
         * @param buildDirectory classpath directory containing the build output.
         * @return this builder.
         */
        public Builder buildDirectory(String buildDirectory) {
            this.buildDirectory = buildDirectory;
            return this;
        }

        /**
         * @param manifestPath classpath location of the manifest, or {@code null} for
         *                     {@code {buildDirectory}/.vite/manifest.json}.
         * @return this builder.
         */
        public Builder manifestPath(@Nullable String manifestPath) {
            this.manifestPath = manifestPath;
            return this;
        }

        /**
         * @param publicPath URL prefix of built files.
         * @return this builder.
         */
        public Builder publicPath(String publicPath) {
            this.publicPath = publicPath;
            return this;
        }

        /**
         * @return the configuration.
         */
        public ViteConfig build() {
            String normalisedBuildDirectory = trimSlashes(buildDirectory);
            String normalisedManifestPath = manifestPath == null
                ? normalisedBuildDirectory + "/.vite/manifest.json"
                : trimSlashes(manifestPath);

            return new ViteConfig(
                hotFile,
                normalisedBuildDirectory,
                normalisedManifestPath,
                normalisePublicPath(publicPath)
            );
        }

        private static String trimSlashes(String path) {
            int start = 0;
            int end = path.length();

            while (start < end && path.charAt(start) == '/') {
                start++;
            }

            while (end > start && path.charAt(end - 1) == '/') {
                end--;
            }

            return path.substring(start, end);
        }

        private static String normalisePublicPath(String path) {
            String trimmed = trimSlashes(path);

            if (trimmed.isEmpty()) {
                return "/";
            }

            return "/" + trimmed + "/";
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :inertia4j.core:test --tests 'io.github.inertia4j.core.vite.ViteConfigTest'`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add inertia4j.core/src/main/java/io/github/inertia4j/core/vite/ViteConfig.java inertia4j.core/src/test/java/io/github/inertia4j/core/vite/ViteConfigTest.java
git commit -m "Add Vite integration configuration

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Production tags and asset version

**Files:**
- Create: `inertia4j.core/src/main/java/io/github/inertia4j/core/vite/Vite.java`
- Create: `inertia4j.core/src/main/java/io/github/inertia4j/core/vite/ViteVersionProvider.java`
- Create: `inertia4j.core/src/test/resources/vite-fixture/.vite/manifest.json`
- Create: `inertia4j.core/src/test/resources/vite-malformed/.vite/manifest.json`
- Create: `inertia4j.core/src/test/resources/vite-dangling/.vite/manifest.json`
- Test: `inertia4j.core/src/test/java/io/github/inertia4j/core/vite/ViteProductionTest.java`

**Interfaces:**
- Consumes: `ViteConfig` (Task 3), `ViteManifest`, `ManifestChunk` (Task 2), `ViteException` (Task 1).
- Produces: `public Vite(ViteConfig config)`, `ViteConfig getConfig()`, `String tags(String... entries)`, `String version()`; `public ViteVersionProvider(Vite vite) implements Supplier<String>`. Task 5 adds dev mode to this same class.

- [ ] **Step 1: Create the fixtures**

`inertia4j.core/src/test/resources/vite-fixture/.vite/manifest.json`:

```json
{
  "_shared-B7PI925R.js": {
    "file": "assets/shared-B7PI925R.js",
    "name": "shared",
    "imports": ["_util-D1gK2mQx.js"],
    "css": ["assets/shared-ChJ_j-JJ.css"]
  },
  "_util-D1gK2mQx.js": {
    "file": "assets/util-D1gK2mQx.js",
    "name": "util",
    "css": ["assets/util-Cq0cE3aP.css"]
  },
  "_a-Xy12Ab34.js": {
    "file": "assets/a-Xy12Ab34.js",
    "imports": ["_b-Zt56Cd78.js"]
  },
  "_b-Zt56Cd78.js": {
    "file": "assets/b-Zt56Cd78.js",
    "imports": ["_a-Xy12Ab34.js"]
  },
  "baz.js": {
    "file": "assets/baz-B2H3sXNv.js",
    "name": "baz",
    "src": "baz.js",
    "isDynamicEntry": true
  },
  "views/foo.js": {
    "file": "assets/foo-BRBmoGS9.js",
    "name": "foo",
    "src": "views/foo.js",
    "isEntry": true,
    "imports": ["_shared-B7PI925R.js"],
    "css": ["assets/foo-5UjPuW-k.css"]
  },
  "views/bar.js": {
    "file": "assets/bar-gkvgaI9m.js",
    "name": "bar",
    "src": "views/bar.js",
    "isEntry": true,
    "imports": ["_shared-B7PI925R.js"],
    "dynamicImports": ["baz.js"]
  },
  "views/cycle.js": {
    "file": "assets/cycle-C9dE0fGh.js",
    "name": "cycle",
    "src": "views/cycle.js",
    "isEntry": true,
    "imports": ["_a-Xy12Ab34.js"]
  },
  "styles/app.css": {
    "file": "assets/app-DfP3c1rW.css",
    "src": "styles/app.css",
    "isEntry": true
  }
}
```

`inertia4j.core/src/test/resources/vite-malformed/.vite/manifest.json`:

```json
{"views/foo.js": {"file": "assets/foo.js"
```

`inertia4j.core/src/test/resources/vite-dangling/.vite/manifest.json`:

```json
{"views/foo.js": {"file": "assets/foo.js", "isEntry": true, "imports": ["_gone.js"]}}
```

- [ ] **Step 2: Write the failing test**

```java
package io.github.inertia4j.core.vite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteProductionTest {
    @TempDir
    Path tempDir;

    private Vite vite(String buildDirectory) {
        return new Vite(ViteConfig.builder()
            .hotFile(tempDir.resolve("vite.hot"))
            .buildDirectory(buildDirectory)
            .build());
    }

    private static String sha256OfResource(String path) throws Exception {
        try (InputStream inputStream = ViteProductionTest.class.getClassLoader().getResourceAsStream(path)) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(inputStream.readAllBytes());
            return String.format("%064x", new BigInteger(1, digest));
        }
    }

    @Test
    void tags_whenEntryImportsChunks_emitsCssThenEntryThenPreloads() {
        String expected = String.join("\n",
            "<link rel=\"stylesheet\" href=\"/build/assets/foo-5UjPuW-k.css\">",
            "<link rel=\"stylesheet\" href=\"/build/assets/shared-ChJ_j-JJ.css\">",
            "<link rel=\"stylesheet\" href=\"/build/assets/util-Cq0cE3aP.css\">",
            "<script type=\"module\" src=\"/build/assets/foo-BRBmoGS9.js\"></script>",
            "<link rel=\"modulepreload\" href=\"/build/assets/shared-B7PI925R.js\">",
            "<link rel=\"modulepreload\" href=\"/build/assets/util-D1gK2mQx.js\">"
        );

        assertEquals(expected, vite("vite-fixture").tags("views/foo.js"));
    }

    @Test
    void tags_whenEntriesShareChunks_emitsEachUrlOnce() {
        String expected = String.join("\n",
            "<link rel=\"stylesheet\" href=\"/build/assets/foo-5UjPuW-k.css\">",
            "<link rel=\"stylesheet\" href=\"/build/assets/shared-ChJ_j-JJ.css\">",
            "<link rel=\"stylesheet\" href=\"/build/assets/util-Cq0cE3aP.css\">",
            "<script type=\"module\" src=\"/build/assets/foo-BRBmoGS9.js\"></script>",
            "<script type=\"module\" src=\"/build/assets/bar-gkvgaI9m.js\"></script>",
            "<link rel=\"modulepreload\" href=\"/build/assets/shared-B7PI925R.js\">",
            "<link rel=\"modulepreload\" href=\"/build/assets/util-D1gK2mQx.js\">"
        );

        assertEquals(expected, vite("vite-fixture").tags("views/foo.js", "views/bar.js"));
    }

    @Test
    void tags_whenEntryIsStylesheet_emitsStylesheetLink() {
        assertEquals(
            "<link rel=\"stylesheet\" href=\"/build/assets/app-DfP3c1rW.css\">",
            vite("vite-fixture").tags("styles/app.css")
        );
    }

    @Test
    void tags_ignoresDynamicImports() {
        assertFalse(vite("vite-fixture").tags("views/bar.js").contains("baz"));
    }

    @Test
    void tags_whenImportsAreCircular_terminates() {
        String expected = String.join("\n",
            "<script type=\"module\" src=\"/build/assets/cycle-C9dE0fGh.js\"></script>",
            "<link rel=\"modulepreload\" href=\"/build/assets/a-Xy12Ab34.js\">",
            "<link rel=\"modulepreload\" href=\"/build/assets/b-Zt56Cd78.js\">"
        );

        assertEquals(expected, vite("vite-fixture").tags("views/cycle.js"));
    }

    @Test
    void tags_whenEntryHasLeadingSlash_resolvesManifestKey() {
        Vite vite = vite("vite-fixture");

        assertEquals(vite.tags("views/foo.js"), vite.tags("/views/foo.js"));
    }

    @Test
    void tags_usesConfiguredPublicPath() {
        Vite vite = new Vite(ViteConfig.builder()
            .hotFile(tempDir.resolve("vite.hot"))
            .buildDirectory("vite-fixture")
            .publicPath("/static/")
            .build());

        assertEquals(
            "<link rel=\"stylesheet\" href=\"/static/assets/app-DfP3c1rW.css\">",
            vite.tags("styles/app.css")
        );
    }

    @Test
    void tags_whenEntryIsUnknown_throwsListingEntries() {
        ViteException exception = assertThrows(
            ViteException.class,
            () -> vite("vite-fixture").tags("views/missing.js")
        );

        assertEquals(
            "Unable to locate 'views/missing.js' in the Vite manifest. "
                + "Available entries: views/foo.js, views/bar.js, views/cycle.js, styles/app.css",
            exception.getMessage()
        );
    }

    @Test
    void tags_whenManifestIsMissing_throwsActionableError() {
        ViteException exception = assertThrows(
            ViteException.class,
            () -> vite("vite-missing").tags("views/foo.js")
        );

        assertEquals(
            "Vite manifest not found at classpath:vite-missing/.vite/manifest.json. "
                + "Start the Vite dev server or run the frontend build.",
            exception.getMessage()
        );
    }

    @Test
    void tags_whenManifestIsMalformed_throws() {
        ViteException exception = assertThrows(
            ViteException.class,
            () -> vite("vite-malformed").tags("views/foo.js")
        );

        assertTrue(exception.getMessage().startsWith("Invalid Vite manifest:"));
    }

    @Test
    void tags_whenImportIsMissingFromManifest_throws() {
        ViteException exception = assertThrows(
            ViteException.class,
            () -> vite("vite-dangling").tags("views/foo.js")
        );

        assertEquals(
            "Invalid Vite manifest: chunk 'views/foo.js' imports unknown chunk '_gone.js'",
            exception.getMessage()
        );
    }

    @Test
    void tags_whenNoEntryIsGiven_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> vite("vite-fixture").tags());

        assertEquals("At least one Vite entry is required", exception.getMessage());
    }

    @Test
    void version_whenManifestIsPresent_returnsSha256OfManifest() throws Exception {
        Vite vite = vite("vite-fixture");

        assertEquals(sha256OfResource("vite-fixture/.vite/manifest.json"), vite.version());
        assertEquals(vite.version(), vite.version());
    }

    @Test
    void version_whenManifestIsMissing_returnsDefault() {
        assertEquals("1", vite("vite-missing").version());
    }

    @Test
    void version_whenManifestIsMalformed_returnsDefault() {
        assertEquals("1", vite("vite-malformed").version());
    }

    @Test
    void viteVersionProvider_delegatesToVite() {
        Vite vite = vite("vite-fixture");

        assertEquals(vite.version(), new ViteVersionProvider(vite).get());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :inertia4j.core:test --tests 'io.github.inertia4j.core.vite.ViteProductionTest'`
Expected: FAIL — compilation error, `Vite` not found.

- [ ] **Step 4: Write minimal implementation**

`Vite.java`:

```java
package io.github.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Produces the HTML tags loading Vite entries, following the Vite backend integration guide,
 * and the Inertia asset version derived from the Vite manifest.
 * Thread-safe; one instance is meant to be shared by the whole application.
 *
 * @see <a href="https://vite.dev/guide/backend-integration.html">Vite backend integration</a>
 */
@NullMarked
public class Vite {
    private static final String defaultVersion = "1";
    private static final Pattern stylesheetPattern =
        Pattern.compile("\\.(css|less|sass|scss|styl|stylus|pcss|postcss)(\\?.*)?$");

    private final ViteConfig config;
    private volatile @Nullable LoadedManifest loadedManifest;

    /**
     * @param config integration settings.
     */
    public Vite(ViteConfig config) {
        this.config = config;
    }

    /**
     * @return integration settings.
     */
    public ViteConfig getConfig() {
        return config;
    }

    /**
     * Renders the tags loading the given entries.
     *
     * @param entries manifest keys, such as {@code src/main.tsx}; a leading slash is ignored.
     * @return tags separated by new lines.
     * @throws ViteException if no entry is given, the manifest is missing or invalid, or an entry is unknown.
     */
    public String tags(String... entries) {
        if (entries.length == 0) {
            throw new ViteException("At least one Vite entry is required");
        }

        List<String> normalisedEntries = new ArrayList<>();

        for (String entry : entries) {
            normalisedEntries.add(stripLeadingSlash(entry));
        }

        return productionTags(normalisedEntries);
    }

    /**
     * @return SHA-256 of the manifest, or {@code "1"} when the manifest is missing or invalid.
     */
    public String version() {
        try {
            return manifest().hash;
        } catch (ViteException e) {
            return defaultVersion;
        }
    }

    private String productionTags(List<String> entries) {
        ViteManifest manifest = manifest().manifest;
        Set<String> stylesheets = new LinkedHashSet<>();
        Set<String> entryScripts = new LinkedHashSet<>();
        Set<String> preloads = new LinkedHashSet<>();

        for (String entry : entries) {
            ManifestChunk chunk = manifest.chunk(entry).orElseThrow(() -> new ViteException(
                "Unable to locate '" + entry + "' in the Vite manifest. Available entries: "
                    + String.join(", ", manifest.entries())
            ));

            collectStylesheets(manifest, entry, chunk, stylesheets, new HashSet<>());

            if (isStylesheet(chunk.getFile())) {
                stylesheets.add(url(chunk.getFile()));
            } else {
                entryScripts.add(url(chunk.getFile()));
            }

            collectPreloads(manifest, entry, chunk, preloads, new HashSet<>());
        }

        preloads.removeAll(entryScripts);

        List<String> tags = new ArrayList<>();
        stylesheets.forEach(href -> tags.add(stylesheetTag(href)));
        entryScripts.forEach(src -> tags.add(scriptTag(src)));
        preloads.forEach(href -> tags.add("<link rel=\"modulepreload\" href=\"" + escapeHtml(href) + "\">"));

        return String.join("\n", tags);
    }

    private void collectStylesheets(
        ViteManifest manifest,
        String key,
        ManifestChunk chunk,
        Set<String> stylesheets,
        Set<String> visited
    ) {
        if (!visited.add(key)) {
            return;
        }

        chunk.getCss().forEach(file -> stylesheets.add(url(file)));

        for (String importKey : chunk.getImports()) {
            collectStylesheets(manifest, importKey, importedChunk(manifest, key, importKey), stylesheets, visited);
        }
    }

    private void collectPreloads(
        ViteManifest manifest,
        String key,
        ManifestChunk chunk,
        Set<String> preloads,
        Set<String> visited
    ) {
        for (String importKey : chunk.getImports()) {
            if (!visited.add(importKey)) {
                continue;
            }

            ManifestChunk imported = importedChunk(manifest, key, importKey);
            preloads.add(url(imported.getFile()));
            collectPreloads(manifest, importKey, imported, preloads, visited);
        }
    }

    private static ManifestChunk importedChunk(ViteManifest manifest, String key, String importKey) {
        return manifest.chunk(importKey).orElseThrow(() -> new ViteException(
            "Invalid Vite manifest: chunk '" + key + "' imports unknown chunk '" + importKey + "'"
        ));
    }

    private LoadedManifest manifest() {
        LoadedManifest current = loadedManifest;

        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (loadedManifest == null) {
                loadedManifest = loadManifest();
            }

            return loadedManifest;
        }
    }

    private LoadedManifest loadManifest() {
        String path = config.getManifestPath();

        try (InputStream inputStream = classLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new ViteException(
                    "Vite manifest not found at classpath:" + path
                        + ". Start the Vite dev server or run the frontend build."
                );
            }

            byte[] bytes = inputStream.readAllBytes();

            return new LoadedManifest(ViteManifest.parse(new String(bytes, StandardCharsets.UTF_8)), sha256(bytes));
        } catch (IOException e) {
            throw new ViteException("Unable to read Vite manifest at classpath:" + path, e);
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        if (contextClassLoader != null) {
            return contextClassLoader;
        }

        return Vite.class.getClassLoader();
    }

    private String url(String file) {
        return config.getPublicPath() + file;
    }

    private static boolean isStylesheet(String path) {
        return stylesheetPattern.matcher(path).find();
    }

    private static String scriptTag(String src) {
        return "<script type=\"module\" src=\"" + escapeHtml(src) + "\"></script>";
    }

    private static String stylesheetTag(String href) {
        return "<link rel=\"stylesheet\" href=\"" + escapeHtml(href) + "\">";
    }

    private static String stripLeadingSlash(String path) {
        String stripped = path;

        while (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }

        return stripped;
    }

    private static String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);

            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }

            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static class LoadedManifest {
        private final ViteManifest manifest;
        private final String hash;

        private LoadedManifest(ViteManifest manifest, String hash) {
            this.manifest = manifest;
            this.hash = hash;
        }
    }
}
```

`ViteVersionProvider.java`:

```java
package io.github.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;

/**
 * Inertia asset version supplier backed by {@link Vite#version()}.
 */
@NullMarked
public class ViteVersionProvider implements Supplier<String> {
    private final Vite vite;

    /**
     * @param vite Vite integration providing the version.
     */
    public ViteVersionProvider(Vite vite) {
        this.vite = vite;
    }

    @Override
    public String get() {
        return vite.version();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :inertia4j.core:test --tests 'io.github.inertia4j.core.vite.ViteProductionTest'`
Expected: PASS (16 tests).

- [ ] **Step 6: Commit**

```bash
git add inertia4j.core/src/main/java/io/github/inertia4j/core/vite inertia4j.core/src/test
git commit -m "Render Vite production tags and derive asset version from manifest

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Dev server mode and React refresh preamble

**Files:**
- Modify: `inertia4j.core/src/main/java/io/github/inertia4j/core/vite/Vite.java`
- Test: `inertia4j.core/src/test/java/io/github/inertia4j/core/vite/ViteDevModeTest.java`

**Interfaces:**
- Consumes: `Vite`, fixtures from Task 4.
- Produces on `Vite`: `boolean isDevMode()`, `String devServerUrl()`, `String reactRefreshTag()`; `tags(...)` and `version()` now honour dev mode.

- [ ] **Step 1: Write the failing test**

```java
package io.github.inertia4j.core.vite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteDevModeTest {
    @TempDir
    Path tempDir;

    private Path hotFile;
    private Vite vite;

    @BeforeEach
    void setUp() {
        hotFile = tempDir.resolve("vite.hot");
        vite = new Vite(ViteConfig.builder().hotFile(hotFile).buildDirectory("vite-fixture").build());
    }

    private void startDevServer(String hotFileContent) throws IOException {
        Files.writeString(hotFile, hotFileContent);
    }

    @Test
    void isDevMode_whenHotFileIsMissing_isFalse() {
        assertFalse(vite.isDevMode());
    }

    @Test
    void isDevMode_whenHotFileHasUrl_isTrue() throws IOException {
        startDevServer("http://localhost:5173");

        assertTrue(vite.isDevMode());
    }

    @Test
    void isDevMode_whenHotFileIsBlank_isFalse() throws IOException {
        startDevServer("  \n");

        assertFalse(vite.isDevMode());
    }

    @Test
    void devServerUrl_trimsWhitespaceAndTrailingSlash() throws IOException {
        startDevServer("http://localhost:5173/\n");

        assertEquals("http://localhost:5173", vite.devServerUrl());
    }

    @Test
    void devServerUrl_whenNotInDevMode_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> vite.devServerUrl());

        assertEquals("Vite dev server is not running: no URL in hot file " + hotFile, exception.getMessage());
    }

    @Test
    void devServerUrl_whenHotFileIsRewritten_returnsNewUrl() throws IOException {
        startDevServer("http://localhost:5173");
        vite.devServerUrl();
        startDevServer("http://localhost:5174");
        Files.setLastModifiedTime(hotFile, FileTime.fromMillis(Files.getLastModifiedTime(hotFile).toMillis() + 10_000));

        assertEquals("http://localhost:5174", vite.devServerUrl());
    }

    @Test
    void tags_inDevMode_emitsClientThenEntries() throws IOException {
        startDevServer("http://localhost:5173/");

        String expected = String.join("\n",
            "<script type=\"module\" src=\"http://localhost:5173/@vite/client\"></script>",
            "<script type=\"module\" src=\"http://localhost:5173/src/main.tsx\"></script>",
            "<link rel=\"stylesheet\" href=\"http://localhost:5173/src/app.css\">"
        );

        assertEquals(expected, vite.tags("src/main.tsx", "/src/app.css"));
    }

    @Test
    void tags_inDevMode_doesNotReadManifest() throws IOException {
        startDevServer("http://localhost:5173");
        Vite viteWithoutBuild = new Vite(ViteConfig.builder().hotFile(hotFile).buildDirectory("vite-missing").build());

        assertTrue(viteWithoutBuild.tags("src/main.tsx").contains("http://localhost:5173/src/main.tsx"));
    }

    @Test
    void tags_inDevMode_escapesAttributeValues() throws IOException {
        startDevServer("http://localhost:5173");

        assertTrue(vite.tags("src/a&b\".ts").contains("src=\"http://localhost:5173/src/a&amp;b&quot;.ts\""));
    }

    @Test
    void tags_whenHotFileIsRemoved_fallsBackToManifest() throws IOException {
        startDevServer("http://localhost:5173");
        vite.tags("views/foo.js");
        Files.delete(hotFile);

        assertTrue(vite.tags("views/foo.js").contains("src=\"/build/assets/foo-BRBmoGS9.js\""));
    }

    @Test
    void reactRefreshTag_inDevMode_emitsPreamble() throws IOException {
        startDevServer("http://localhost:5173");

        String expected = String.join("\n",
            "<script type=\"module\">",
            "  import RefreshRuntime from 'http://localhost:5173/@react-refresh'",
            "  RefreshRuntime.injectIntoGlobalHook(window)",
            "  window.$RefreshReg$ = () => {}",
            "  window.$RefreshSig$ = () => (type) => type",
            "  window.__vite_plugin_react_preamble_installed__ = true",
            "</script>"
        );

        assertEquals(expected, vite.reactRefreshTag());
    }

    @Test
    void reactRefreshTag_inProduction_isEmpty() {
        assertEquals("", vite.reactRefreshTag());
    }

    @Test
    void version_inDevMode_isDev() throws IOException {
        startDevServer("http://localhost:5173");

        assertEquals("dev", vite.version());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :inertia4j.core:test --tests 'io.github.inertia4j.core.vite.ViteDevModeTest'`
Expected: FAIL — compilation error, `isDevMode()` / `devServerUrl()` / `reactRefreshTag()` not found.

- [ ] **Step 3: Implement dev mode in `Vite.java`**

Add imports:

```java
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
```

Add constants and field next to the existing ones:

```java
    private static final String devVersion = "dev";
    private static final System.Logger logger = System.getLogger(Vite.class.getName());

    private volatile @Nullable HotFile hotFile;
```

Add public methods after `getConfig()`:

```java
    /**
     * @return whether the Vite dev server is running, i.e. the hot file exists and holds a URL.
     */
    public boolean isDevMode() {
        return readDevServerUrl() != null;
    }

    /**
     * @return URL of the running Vite dev server, without trailing slash.
     * @throws ViteException if the dev server is not running.
     */
    public String devServerUrl() {
        String url = readDevServerUrl();

        if (url == null) {
            throw new ViteException("Vite dev server is not running: no URL in hot file " + config.getHotFile());
        }

        return url;
    }

    /**
     * @return the React Fast Refresh preamble in dev mode, an empty string otherwise.
     */
    public String reactRefreshTag() {
        String url = readDevServerUrl();

        if (url == null) {
            return "";
        }

        return String.join("\n",
            "<script type=\"module\">",
            "  import RefreshRuntime from '" + url + "/@react-refresh'",
            "  RefreshRuntime.injectIntoGlobalHook(window)",
            "  window.$RefreshReg$ = () => {}",
            "  window.$RefreshSig$ = () => (type) => type",
            "  window.__vite_plugin_react_preamble_installed__ = true",
            "</script>"
        );
    }
```

Replace the end of `tags(...)` (`return productionTags(normalisedEntries);`) with:

```java
        String devServerUrl = readDevServerUrl();

        if (devServerUrl != null) {
            return devTags(devServerUrl, normalisedEntries);
        }

        return productionTags(normalisedEntries);
```

Replace `version()` with:

```java
    /**
     * @return {@code "dev"} in dev mode, otherwise the SHA-256 of the manifest,
     * or {@code "1"} when the manifest is missing or invalid.
     */
    public String version() {
        if (readDevServerUrl() != null) {
            return devVersion;
        }

        try {
            return manifest().hash;
        } catch (ViteException e) {
            return defaultVersion;
        }
    }
```

Add private methods:

```java
    private String devTags(String devServerUrl, List<String> entries) {
        List<String> tags = new ArrayList<>();
        tags.add(scriptTag(devServerUrl + "/@vite/client"));

        for (String entry : entries) {
            String url = devServerUrl + "/" + entry;
            tags.add(isStylesheet(entry) ? stylesheetTag(url) : scriptTag(url));
        }

        return String.join("\n", tags);
    }

    private @Nullable String readDevServerUrl() {
        Path path = config.getHotFile();

        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }

            FileTime modified = Files.getLastModifiedTime(path);
            long size = Files.size(path);
            HotFile cached = hotFile;

            if (cached != null && cached.modified.equals(modified) && cached.size == size) {
                return cached.url;
            }

            String url = stripTrailingSlash(Files.readString(path).trim());
            HotFile current = new HotFile(modified, size, url.isEmpty() ? null : url);
            hotFile = current;

            return current.url;
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            logger.log(System.Logger.Level.WARNING, "Unable to read Vite hot file " + path, e);
            return null;
        }
    }

    private static String stripTrailingSlash(String url) {
        String stripped = url;

        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }

        return stripped;
    }
```

Add the nested class next to `LoadedManifest`:

```java
    private static class HotFile {
        private final FileTime modified;
        private final long size;
        private final @Nullable String url;

        private HotFile(FileTime modified, long size, @Nullable String url) {
            this.modified = modified;
            this.size = size;
            this.url = url;
        }
    }
```

Update the class Javadoc first paragraph to: "Produces the HTML tags loading Vite entries from the Vite dev server when its hot file exists, or from the build manifest otherwise, following the Vite backend integration guide, and the Inertia asset version."

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :inertia4j.core:test --tests 'io.github.inertia4j.core.vite.*'`
Expected: PASS (all tests of Tasks 1–5).

- [ ] **Step 5: Commit**

```bash
git add inertia4j.core/src/main/java/io/github/inertia4j/core/vite/Vite.java inertia4j.core/src/test/java/io/github/inertia4j/core/vite/ViteDevModeTest.java
git commit -m "Detect Vite dev server through hot file

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Template placeholders

**Files:**
- Modify: `inertia4j.core/src/main/java/io/github/inertia4j/core/SimpleTemplateRenderer.java`
- Create: `inertia4j.core/src/test/resources/templates/vite.html`
- Create: `inertia4j.core/src/test/resources/templates/vite-empty-entry.html`
- Test: `inertia4j.core/src/test/java/io/github/inertia4j/core/SimpleTemplateRendererTest.java`

**Interfaces:**
- Consumes: `Vite.tags(String...)`, `Vite.reactRefreshTag()` (Tasks 4–5).
- Produces: `public SimpleTemplateRenderer(String templatePath, @Nullable Vite vite)`; existing `SimpleTemplateRenderer(String)` delegates with `null`.

- [ ] **Step 1: Create the templates**

`inertia4j.core/src/test/resources/templates/vite.html` (exact content, trailing newline):

```html
<head>
@ViteReactRefresh@
@Vite(views/foo.js, styles/app.css)@
</head>
<div id="app" data-page='@PageObject@'></div>
```

`inertia4j.core/src/test/resources/templates/vite-empty-entry.html`:

```html
@Vite(views/foo.js, )@
<div id="app" data-page='@PageObject@'></div>
```

- [ ] **Step 2: Write the failing test**

```java
package io.github.inertia4j.core;

import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.core.vite.ViteConfig;
import io.github.inertia4j.core.vite.ViteException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleTemplateRendererTest {
    @TempDir
    Path tempDir;

    private Vite vite() {
        return new Vite(ViteConfig.builder()
            .hotFile(tempDir.resolve("vite.hot"))
            .buildDirectory("vite-fixture")
            .build());
    }

    @Test
    void render_withoutVite_leavesVitePlaceholdersUntouched() {
        String html = new SimpleTemplateRenderer("templates/vite.html").render("{}");

        assertEquals(
            "<head>\n@ViteReactRefresh@\n@Vite(views/foo.js, styles/app.css)@\n</head>\n<div id=\"app\" data-page='{}'></div>\n",
            html
        );
    }

    @Test
    void render_withVite_replacesPlaceholdersWithTags() {
        Vite vite = vite();

        String html = new SimpleTemplateRenderer("templates/vite.html", vite).render("{}");

        assertEquals(
            "<head>\n\n" + vite.tags("views/foo.js", "styles/app.css") + "\n</head>\n<div id=\"app\" data-page='{}'></div>\n",
            html
        );
    }

    @Test
    void render_whenPageObjectContainsVitePlaceholder_doesNotSubstituteIt() {
        String html = new SimpleTemplateRenderer("templates/vite.html", vite()).render("{\"x\":\"@Vite(views/foo.js)@\"}");

        assertTrue(html.contains("data-page='{&quot;x&quot;:&quot;@Vite(views/foo.js)@&quot;}'"));
    }

    @Test
    void render_inDevMode_keepsDollarSignsOfReactPreamble() throws Exception {
        Files.writeString(tempDir.resolve("vite.hot"), "http://localhost:5173");

        String html = new SimpleTemplateRenderer("templates/vite.html", vite()).render("{}");

        assertTrue(html.contains("  window.$RefreshReg$ = () => {}\n"));
        assertTrue(html.contains("<script type=\"module\" src=\"http://localhost:5173/views/foo.js\"></script>"));
    }

    @Test
    void render_whenPlaceholderHasEmptyEntry_throws() {
        SimpleTemplateRenderer renderer = new SimpleTemplateRenderer("templates/vite-empty-entry.html", vite());

        ViteException exception = assertThrows(ViteException.class, () -> renderer.render("{}"));

        assertEquals("Empty entry in Vite placeholder @Vite(views/foo.js, )@", exception.getMessage());
    }

    @Test
    void render_whenCalledConcurrently_rendersEachPageObject() throws Exception {
        SimpleTemplateRenderer renderer = new SimpleTemplateRenderer("templates/vite.html", vite());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<String>> results = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            String pageObject = "{\"n\":" + i + "}";
            results.add(executor.submit(() -> renderer.render(pageObject)));
        }

        for (int i = 0; i < 200; i++) {
            assertTrue(results.get(i).get().contains("data-page='{&quot;n&quot;:" + i + "}'"));
        }

        executor.shutdown();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :inertia4j.core:test --tests 'io.github.inertia4j.core.SimpleTemplateRendererTest'`
Expected: FAIL — compilation error, no `SimpleTemplateRenderer(String, Vite)` constructor.

- [ ] **Step 4: Rewrite `SimpleTemplateRenderer.java`**

```java
package io.github.inertia4j.core;

import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.core.vite.ViteException;
import io.github.inertia4j.spi.TemplateRenderer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple {@link TemplateRenderer} implementation used by default if no specific renderer is provided.
 * It loads a template file from the classpath and replaces a placeholder with the page object JSON.
 * When given a {@link Vite} instance, it also replaces the <code>@Vite(entry, ...)@</code> and
 * <code>@ViteReactRefresh@</code> placeholders with the tags loading the frontend.
 */
@NullMarked
public class SimpleTemplateRenderer implements TemplateRenderer {
    private static final Pattern pageObjectPattern = Pattern.compile("@PageObject@");
    private static final Pattern vitePattern = Pattern.compile("@Vite\\(([^)]*)\\)@");
    private static final String reactRefreshPlaceholder = "@ViteReactRefresh@";

    private final String template;
    private final @Nullable Vite vite;

    /**
     * Constructs a SimpleTemplateRenderer without Vite support.
     *
     * @param templatePath Classpath path to the HTML template file (e.g., "/templates/app.html").
     * @throws TemplateRenderingException if the template file cannot be loaded or read.
     */
    public SimpleTemplateRenderer(String templatePath) throws TemplateRenderingException {
        this(templatePath, null);
    }

    /**
     * Constructs a SimpleTemplateRenderer.
     * Loads the template from the specified classpath resource path and prepares it for rendering.
     *
     * @param templatePath Classpath path to the HTML template file (e.g., "/templates/app.html").
     * @param vite Vite integration replacing the Vite placeholders, or {@code null} to leave them untouched.
     * @throws TemplateRenderingException if the template file cannot be loaded or read.
     */
    public SimpleTemplateRenderer(String templatePath, @Nullable Vite vite) throws TemplateRenderingException {
        this.template = loadTemplate(templatePath);
        this.vite = vite;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation first replaces the Vite placeholders, then the first occurrence of the
     * <code>@PageObject@</code> placeholder with the provided {@code pageObjectJson}, escaping HTML characters.
     */
    @Override
    public String render(String pageObjectJson) {
        String escapedPageObjectJson = pageObjectJson
            .replace("\\", "\\\\")
            .replace("$", "\\$")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");

        return pageObjectPattern.matcher(renderVitePlaceholders()).replaceFirst(escapedPageObjectJson);
    }

    private String renderVitePlaceholders() {
        if (vite == null) {
            return template;
        }

        String withReactRefresh = template.replace(reactRefreshPlaceholder, vite.reactRefreshTag());
        Matcher matcher = vitePattern.matcher(withReactRefresh);
        StringBuilder rendered = new StringBuilder();

        while (matcher.find()) {
            String tags = vite.tags(parseEntries(matcher.group(), matcher.group(1)));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(tags));
        }

        matcher.appendTail(rendered);

        return rendered.toString();
    }

    private static String[] parseEntries(String placeholder, String entryList) {
        String[] entries = entryList.split(",", -1);

        for (int i = 0; i < entries.length; i++) {
            entries[i] = entries[i].trim();

            if (entries[i].isEmpty()) {
                throw new ViteException("Empty entry in Vite placeholder " + placeholder);
            }
        }

        return entries;
    }

    /**
     * Loads the template content from the specified classpath resource path.
     *
     * @param path The classpath path to the template file.
     * @return The content of the template file as a String.
     * @throws TemplateRenderingException if the template file cannot be found or read.
     */
    private String loadTemplate(String path) throws TemplateRenderingException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (InputStream inputStream = classLoader.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new TemplateRenderingException(path);
            }
            return new String(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new TemplateRenderingException(path, e);
        }
    }
}
```

- [ ] **Step 5: Run the whole core suite**

Run: `./gradlew :inertia4j.core:test`
Expected: PASS, including the existing `InertiaRendererTest`.

- [ ] **Step 6: Commit**

```bash
git add inertia4j.core/src
git commit -m "Support Vite placeholders in SimpleTemplateRenderer

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Spring Boot wiring

**Files:**
- Modify: `build-logic/src/main/kotlin/inertia4j.spring-conventions.gradle.kts`
- Modify: `inertia4j.spring-shared/src/main/java/io/github/inertia4j/springshared/InertiaConfigurationProperties.java`
- Modify: `inertia4j.spring-shared/src/main/java/io/github/inertia4j/springshared/AbstractInertiaSpringAutoconfiguration.java`
- Modify: `inertia4j.spring-boot-3/src/main/java/io/github/inertia4j/springboot3/InertiaSpringAutoconfiguration.java`
- Modify: `inertia4j.spring-boot-4/src/main/java/io/github/inertia4j/springboot4/InertiaSpringAutoconfiguration.java`
- Modify: `inertia4j.spring-boot-3/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `inertia4j.spring-boot-4/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create (both `inertia4j.spring-boot-3` and `inertia4j.spring-boot-4`): `src/test/resources/templates/vite.html`, `src/test/resources/vite-fixture/.vite/manifest.json`, `src/test/resources/vite-fixture/assets/main-BRBmoGS9.js`
- Test: `inertia4j.spring-boot-3/src/test/java/io/github/inertia4j/springboot3/ViteMockMvcTest.java`, `.../springboot3/ViteDisabledMockMvcTest.java`, `inertia4j.spring-boot-4/src/test/java/io/github/inertia4j/springboot4/ViteMockMvcTest.java`

**Interfaces:**
- Consumes: `Vite`, `ViteConfig`, `SimpleTemplateRenderer(String, Vite)` (Tasks 3–6).
- Produces: `InertiaConfigurationProperties.ViteProperties` (package-private fields `enabled`, `hotFile`, `buildDirectory`, `manifest`, `publicPath`, `cacheMaxAge`; method `ViteConfig toViteConfig()`); beans `Vite vite()`, `VersionProvider versionProvider(Vite)`, `TemplateRenderer templateRenderer(Vite)`, `WebMvcConfigurer inertiaViteAssets(Vite)`.

- [ ] **Step 1: Create the test fixtures (identical in both Boot modules)**

`src/test/resources/templates/vite.html`:

```html
<!doctype html>
<html lang="en">
  <head>
    @Vite(src/main.tsx)@
  </head>
  <body>
    <div id="app" data-page='@PageObject@'></div>
  </body>
</html>
```

`src/test/resources/vite-fixture/.vite/manifest.json`:

```json
{
  "src/main.tsx": {
    "file": "assets/main-BRBmoGS9.js",
    "name": "main",
    "src": "src/main.tsx",
    "isEntry": true,
    "css": ["assets/main-5UjPuW-k.css"]
  }
}
```

`src/test/resources/vite-fixture/assets/main-BRBmoGS9.js`:

```js
console.log("inertia4j");
```

- [ ] **Step 2: Write the failing tests**

`inertia4j.spring-boot-3/src/test/java/io/github/inertia4j/springboot3/ViteMockMvcTest.java`:

```java
package io.github.inertia4j.springboot3;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {ViteMockMvcTest.FakeApplication.class, ViteMockMvcTest.FakeController.class},
    properties = {
        "inertia.template-path=templates/vite.html",
        "inertia.vite.build-directory=vite-fixture",
        "inertia.vite.cache-max-age=1h"
    }
)
@AutoConfigureMockMvc
class ViteMockMvcTest {
    @Autowired
    MockMvc mvc;

    @SpringBootApplication
    static class FakeApplication {
    }

    @RestController
    static class FakeController {
        @Autowired
        Inertia inertia;

        @GetMapping("/")
        ResponseEntity<String> index() {
            return inertia.render("Home", Map.of());
        }
    }

    private static String manifestHash() throws Exception {
        try (InputStream inputStream = ViteMockMvcTest.class.getClassLoader()
            .getResourceAsStream("vite-fixture/.vite/manifest.json")) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(inputStream.readAllBytes());
            return String.format("%064x", new BigInteger(1, digest));
        }
    }

    @Test
    void fullPageVisit_rendersViteTagsFromManifest() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                "<link rel=\"stylesheet\" href=\"/build/assets/main-5UjPuW-k.css\">\n"
                    + "<script type=\"module\" src=\"/build/assets/main-BRBmoGS9.js\"></script>"
            )));
    }

    @Test
    void inertiaVisit_whenAssetVersionIsStale_returnsConflict() throws Exception {
        mvc.perform(get("/").header("X-Inertia", "true").header("X-Inertia-Version", "stale"))
            .andExpect(status().isConflict());
    }

    @Test
    void inertiaVisit_whenAssetVersionIsCurrent_returnsPage() throws Exception {
        String version = manifestHash();

        mvc.perform(get("/").header("X-Inertia", "true").header("X-Inertia-Version", version))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(version));
    }

    @Test
    void builtAsset_isServedWithImmutableCacheHeaders() throws Exception {
        mvc.perform(get("/build/assets/main-BRBmoGS9.js"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "max-age=3600, public, immutable"));
    }
}
```

`inertia4j.spring-boot-3/src/test/java/io/github/inertia4j/springboot3/ViteDisabledMockMvcTest.java`:

```java
package io.github.inertia4j.springboot3;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {ViteDisabledMockMvcTest.FakeApplication.class, ViteDisabledMockMvcTest.FakeController.class},
    properties = {
        "inertia.template-path=templates/vite.html",
        "inertia.vite.enabled=false",
        "inertia.vite.build-directory=vite-fixture"
    }
)
@AutoConfigureMockMvc
class ViteDisabledMockMvcTest {
    @Autowired
    MockMvc mvc;

    @SpringBootApplication
    static class FakeApplication {
    }

    @RestController
    static class FakeController {
        @Autowired
        Inertia inertia;

        @GetMapping("/")
        ResponseEntity<String> index() {
            return inertia.render("Home", Map.of());
        }
    }

    @Test
    void fullPageVisit_leavesPlaceholderUntouched() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("@Vite(src/main.tsx)@")));
    }

    @Test
    void inertiaVisit_usesDefaultVersion() throws Exception {
        mvc.perform(get("/").header("X-Inertia", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value("1"));
    }

    @Test
    void builtAsset_isNotServed() throws Exception {
        mvc.perform(get("/build/assets/main-BRBmoGS9.js"))
            .andExpect(status().isNotFound());
    }
}
```

`inertia4j.spring-boot-4/src/test/java/io/github/inertia4j/springboot4/ViteMockMvcTest.java`: identical to the Boot 3 `ViteMockMvcTest` except `package io.github.inertia4j.springboot4;` and the MockMvc import `import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;` (the Boot 4 location, as in the existing `inertia4j.spring-boot-4/src/test/java/io/github/inertia4j/springboot4/InertiaMockMvcTest.java`).

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :inertia4j.spring-boot-3:test --tests '*Vite*'`
Expected: FAIL — the page has no Vite tags and `/build/assets/main-BRBmoGS9.js` returns 404.

- [ ] **Step 4: Add `spring-webmvc` to the Spring convention plugin**

In `build-logic/src/main/kotlin/inertia4j.spring-conventions.gradle.kts`, after `compileOnly("org.springframework:spring-web")` add:

```kotlin
    compileOnly("org.springframework:spring-webmvc")
```

- [ ] **Step 5: Rewrite `InertiaConfigurationProperties.java`**

```java
package io.github.inertia4j.springshared;

import io.github.inertia4j.core.vite.ViteConfig;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuration properties for Inertia4j integration with Spring Boot.
 * Allows setting the template path, default history encryption behavior and the Vite integration via application
 * properties. Properties are prefixed with `inertia`.
 * <p>
 * Example `application.properties`:
 * <pre>
 * inertia.template-path=templates/my-app.html
 * inertia.encrypt-history=true
 * inertia.vite.build-directory=static/build
 * </pre>
 */
@ConfigurationProperties(prefix = "inertia")
public class InertiaConfigurationProperties {
    private static final String defaultTemplatePath = "templates/app.html";
    private static final boolean defaultEncryptHistory = false;

    /**
     * The classpath path to the main HTML template file used by the default {@link io.github.inertia4j.core.SimpleTemplateRenderer}.
     * Corresponds to the `inertia.template-path` property.
     */
    final String templatePath;
    /**
     * Default value for the encryptHistory flag, determining whether browser history state should be encrypted.
     * Corresponds to the `inertia.encrypt-history` property.
     * @see <a href="https://inertiajs.com/history-encryption">Inertia History Encryption</a>
     */
    final boolean encryptHistory;
    /**
     * Vite integration settings, bound from the `inertia.vite.*` properties.
     */
    final ViteProperties vite;

    /**
     * Constructor used by Spring Boot for property binding.
     * @param templatePath Value of `inertia.template-path`.
     * @param encryptHistory Value of `inertia.encrypt-history`.
     * @param vite Values of `inertia.vite.*`.
     */
    @ConstructorBinding
    public InertiaConfigurationProperties(
        @DefaultValue(defaultTemplatePath) String templatePath,
        @DefaultValue("false") boolean encryptHistory,
        @DefaultValue ViteProperties vite
    ) {
        this.templatePath = templatePath;
        this.encryptHistory = encryptHistory;
        this.vite = vite;
    }

    /**
     * Constructor using the default Vite settings.
     * @param templatePath The template path.
     * @param encryptHistory The encryptHistory flag value.
     */
    public InertiaConfigurationProperties(String templatePath, boolean encryptHistory) {
        this(templatePath, encryptHistory, new ViteProperties());
    }

    /**
     * Constructor using default `encryptHistory`.
     * @param templatePath The template path.
     */
    public InertiaConfigurationProperties(String templatePath) {
        this(templatePath, defaultEncryptHistory);
    }

    /**
     * Constructor using default `templatePath`.
     * @param encryptHistory The encryptHistory flag value.
     */
    public InertiaConfigurationProperties(boolean encryptHistory) {
        this(defaultTemplatePath, encryptHistory);
    }

    /**
     * Constructor using default values for both `templatePath` and `encryptHistory`.
     */
    public InertiaConfigurationProperties() {
        this(defaultTemplatePath, defaultEncryptHistory);
    }

    /**
     * Vite integration settings, prefixed with `inertia.vite`.
     */
    public static class ViteProperties {
        /**
         * Whether the Vite integration is enabled: placeholders, asset version and asset serving.
         */
        final boolean enabled;
        /**
         * File whose presence, written by the Vite dev server, enables dev mode.
         */
        final String hotFile;
        /**
         * Classpath directory containing the Vite build output.
         */
        final String buildDirectory;
        /**
         * Classpath location of the Vite manifest, `null` for `{build-directory}/.vite/manifest.json`.
         */
        final @Nullable String manifest;
        /**
         * URL prefix under which built files are served.
         */
        final String publicPath;
        /**
         * `max-age` of the `Cache-Control` header sent with built files.
         */
        final Duration cacheMaxAge;

        /**
         * Constructor used by Spring Boot for property binding.
         * @param enabled Value of `inertia.vite.enabled`.
         * @param hotFile Value of `inertia.vite.hot-file`.
         * @param buildDirectory Value of `inertia.vite.build-directory`.
         * @param manifest Value of `inertia.vite.manifest`.
         * @param publicPath Value of `inertia.vite.public-path`.
         * @param cacheMaxAge Value of `inertia.vite.cache-max-age`.
         */
        @ConstructorBinding
        public ViteProperties(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("vite.hot") String hotFile,
            @DefaultValue("static/build") String buildDirectory,
            @Nullable String manifest,
            @DefaultValue("/build/") String publicPath,
            @DefaultValue("365d") Duration cacheMaxAge
        ) {
            this.enabled = enabled;
            this.hotFile = hotFile;
            this.buildDirectory = buildDirectory;
            this.manifest = manifest;
            this.publicPath = publicPath;
            this.cacheMaxAge = cacheMaxAge;
        }

        /**
         * Constructor using the default values.
         */
        public ViteProperties() {
            this(true, "vite.hot", "static/build", null, "/build/", Duration.ofDays(365));
        }

        ViteConfig toViteConfig() {
            return ViteConfig.builder()
                .hotFile(Path.of(hotFile))
                .buildDirectory(buildDirectory)
                .manifestPath(manifest)
                .publicPath(publicPath)
                .build();
        }
    }
}
```

- [ ] **Step 6: Rewrite `AbstractInertiaSpringAutoconfiguration.java`**

```java
package io.github.inertia4j.springshared;

import io.github.inertia4j.core.SimpleTemplateRenderer;
import io.github.inertia4j.core.TemplateRenderingException;
import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.core.vite.ViteConfig;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.TemplateRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Spring Boot auto-configuration for Inertia4j.
 * Sets up default beans for {@link AbstractInertia}, {@link VersionProvider},
 * {@link PageObjectSerializer}, {@link TemplateRenderer} and {@link Vite} if they are not
 * already present in the application context, and serves the Vite build output.
 */
public abstract class AbstractInertiaSpringAutoconfiguration {
    @Autowired
    protected InertiaConfigurationProperties properties;

    /**
     * Creates the {@link Vite} integration from the `inertia.vite.*` properties if one doesn't already exist.
     *
     * @return A default Vite bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public Vite vite() {
        return new Vite(properties.vite.toViteConfig());
    }

    /**
     * Creates a default {@link VersionProvider} bean if one doesn't already exist.
     * It returns the Vite asset version, or "1" when the Vite integration is disabled.
     *
     * @param vite The Vite integration.
     * @return A default VersionProvider bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public VersionProvider versionProvider(Vite vite) {
        if (!properties.vite.enabled) {
            return () -> "1";
        }

        return vite::version;
    }

    /**
     * Creates a default {@link PageObjectSerializer} bean if one doesn't already exist.
     *
     * @return A default PageObjectSerializer bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public abstract PageObjectSerializer pageObjectSerializer();

    /**
     * Creates a default {@link TemplateRenderer} bean using {@link SimpleTemplateRenderer}
     * and the template path from {@link InertiaConfigurationProperties} if one doesn't already exist.
     *
     * @param vite The Vite integration, used unless disabled.
     * @return A default TemplateRenderer bean.
     * @throws TemplateRenderingException if the template file cannot be loaded.
     */
    @Bean
    @ConditionalOnMissingBean
    public TemplateRenderer templateRenderer(Vite vite) throws TemplateRenderingException {
        Vite enabledVite = properties.vite.enabled ? vite : null;

        return new SimpleTemplateRenderer(properties.templatePath, enabledVite);
    }

    /**
     * Serves the Vite build directory under the Vite public path with long-lived cache headers.
     *
     * @param vite The Vite integration.
     * @return A WebMvcConfigurer registering the resource handler.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "inertia.vite", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebMvcConfigurer inertiaViteAssets(Vite vite) {
        ViteConfig config = vite.getConfig();
        Duration cacheMaxAge = properties.vite.cacheMaxAge;

        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler(config.getPublicPath() + "**")
                    .addResourceLocations("classpath:/" + config.getBuildDirectory() + "/")
                    .setCacheControl(CacheControl.maxAge(cacheMaxAge).cachePublic().immutable());
            }
        };
    }
}
```

- [ ] **Step 7: Update both Boot auto-configurations**

In `inertia4j.spring-boot-3/.../InertiaSpringAutoconfiguration.java` and `inertia4j.spring-boot-4/.../InertiaSpringAutoconfiguration.java`:

Add imports:

```java
import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.springshared.InertiaConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
```

Annotate the class:

```java
@Configuration
@EnableConfigurationProperties(InertiaConfigurationProperties.class)
public class InertiaSpringAutoconfiguration extends AbstractInertiaSpringAutoconfiguration {
```

Replace the `versionProvider()` override with:

```java
    @Override
    @Bean
    @ConditionalOnMissingBean
    public VersionProvider versionProvider(Vite vite) {
        return super.versionProvider(vite)::get;
    }
```

In both `AutoConfiguration.imports` files, delete the line `io.github.inertia4j.springshared.InertiaConfigurationProperties`, leaving only the module's `InertiaSpringAutoconfiguration`.

- [ ] **Step 8: Run all Spring tests**

Run: `./gradlew :inertia4j.spring-boot-3:test :inertia4j.spring-boot-4:test`
Expected: PASS — new Vite tests plus the existing `Inertia4jSpringApplicationTests` (still version `"1"`: no manifest under `static/build` in test resources) and `InertiaTest`.

- [ ] **Step 9: Commit**

```bash
git add build-logic inertia4j.spring-shared inertia4j.spring-boot-3 inertia4j.spring-boot-4
git commit -m "Wire Vite integration into Spring Boot auto-configuration

Bind inertia.* through constructor binding registered with
@EnableConfigurationProperties instead of a @Configuration bean with
final fields, which JavaBean binding cannot populate.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Ktor wiring

**Files:**
- Create: `inertia4j.ktor/src/main/kotlin/ViteKtorConfiguration.kt`
- Modify: `inertia4j.ktor/src/main/kotlin/InertiaKtorConfiguration.kt`
- Modify: `inertia4j.ktor/src/main/kotlin/InertiaPlugin.kt`
- Create: `inertia4j.ktor/src/test/resources/templates/vite.html`, `inertia4j.ktor/src/test/resources/vite-fixture/.vite/manifest.json`, `inertia4j.ktor/src/test/resources/vite-fixture/assets/main-BRBmoGS9.js` (same content as Task 7 Step 1)
- Test: `inertia4j.ktor/src/test/kotlin/ViteKtorTest.kt`

**Interfaces:**
- Consumes: `Vite`, `ViteConfig`, `SimpleTemplateRenderer(String, Vite)`.
- Produces: `class ViteKtorConfiguration` (`hotFile: Path`, `buildDirectory: String`, `manifestPath: String?`, `publicPath: String`, `serveAssets: Boolean`, `cacheMaxAge: Duration`); `InertiaKtorConfiguration.vite(configure: ViteKtorConfiguration.() -> Unit)`; `InertiaKtorConfiguration.versionProvider` becomes `(() -> String)?` defaulting to `null`.

- [ ] **Step 1: Create the fixtures**

Copy the three files of Task 7 Step 1 to `inertia4j.ktor/src/test/resources/` with the same relative paths and content.

- [ ] **Step 2: Write the failing test**

```kotlin
package io.github.inertia4j.ktor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class ViteKtorTest {
    private fun viteApp(
        configure: InertiaKtorConfiguration.() -> Unit = {},
        block: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        application {
            install(Inertia) {
                templatePath = "templates/vite.html"
                vite {
                    buildDirectory = "vite-fixture"
                    cacheMaxAge = 1.hours
                }
                configure()
            }
            routing {
                get("/") {
                    inertia.render("Home")
                }
            }
        }
        block()
    }

    private fun manifestHash(): String {
        val bytes = javaClass.classLoader.getResourceAsStream("vite-fixture/.vite/manifest.json")!!.readBytes()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `full page visit renders Vite tags from manifest`() = viteApp {
        val body = client.get("/").bodyAsText()

        assertContains(
            body,
            "<link rel=\"stylesheet\" href=\"/build/assets/main-5UjPuW-k.css\">\n" +
                "<script type=\"module\" src=\"/build/assets/main-BRBmoGS9.js\"></script>"
        )
    }

    @Test
    fun `stale asset version returns conflict`() = viteApp {
        val response = client.get("/") {
            header("X-Inertia", "true")
            header("X-Inertia-Version", "stale")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `current asset version returns page`() = viteApp {
        val version = manifestHash()

        val response = client.get("/") {
            header("X-Inertia", "true")
            header("X-Inertia-Version", version)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"version\":\"$version\"")
    }

    @Test
    fun `built asset is served with immutable cache headers`() = viteApp {
        val response = client.get("/build/assets/main-BRBmoGS9.js")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("public, max-age=3600, immutable", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `explicit version provider overrides Vite version`() = viteApp(configure = { versionProvider = { "custom" } }) {
        val response = client.get("/") {
            header("X-Inertia", "true")
        }

        assertContains(response.bodyAsText(), "\"version\":\"custom\"")
    }

    @Test
    fun `assets are not served when disabled`() = viteApp(configure = { vite { serveAssets = false } }) {
        val response = client.get("/build/assets/main-BRBmoGS9.js")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :inertia4j.ktor:test --tests 'io.github.inertia4j.ktor.ViteKtorTest'`
Expected: FAIL — compilation error, unresolved reference `vite`.

- [ ] **Step 4: Create `ViteKtorConfiguration.kt`**

```kotlin
package io.github.inertia4j.ktor

import io.github.inertia4j.core.vite.ViteConfig
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Vite integration settings of the Inertia plugin.
 * Defaults match the `vite.config.ts` documented in `docs/vite.md`.
 */
class ViteKtorConfiguration {
    /**
     * File whose presence, written by the Vite dev server, enables dev mode. Defaults to `vite.hot`.
     */
    var hotFile: Path = Path.of("vite.hot")

    /**
     * Classpath directory containing the Vite build output. Defaults to `static/build`.
     */
    var buildDirectory: String = "static/build"

    /**
     * Classpath location of the Vite manifest. Defaults to `null`, meaning `$buildDirectory/.vite/manifest.json`.
     */
    var manifestPath: String? = null

    /**
     * URL prefix under which built files are served. Defaults to `/build/`.
     */
    var publicPath: String = "/build/"

    /**
     * Whether the plugin serves [buildDirectory] under [publicPath]. Defaults to `true`.
     */
    var serveAssets: Boolean = true

    /**
     * `max-age` of the `Cache-Control` header sent with built files. Defaults to 365 days.
     */
    var cacheMaxAge: Duration = 365.days

    internal fun toViteConfig(): ViteConfig = ViteConfig.builder()
        .hotFile(hotFile)
        .buildDirectory(buildDirectory)
        .manifestPath(manifestPath)
        .publicPath(publicPath)
        .build()
}
```

- [ ] **Step 5: Update `InertiaKtorConfiguration.kt`**

Add import `import io.github.inertia4j.core.vite.Vite`.

Replace the `versionProvider` property and its KDoc with:

```kotlin
    /**
     * Provides the current asset version, compared against the `X-Inertia-Version` header of requests.
     * Defaults to `null`, in which case the Vite asset version is used: a hash of the Vite manifest in production,
     * `"dev"` while the Vite dev server runs, and `"1"` when neither is available.
     */
    var versionProvider: (() -> String)? = null
```

Add after `encryptHistory`:

```kotlin
    internal val viteConfiguration = ViteKtorConfiguration()

    /**
     * Configures the Vite integration.
     *
     * @param configure changes applied to the default [ViteKtorConfiguration].
     */
    fun vite(configure: ViteKtorConfiguration.() -> Unit) {
        viteConfiguration.configure()
    }

    internal val viteInstance: Vite by lazy { Vite(viteConfiguration.toViteConfig()) }

    internal val versionProviderOrDefault: () -> String get() {
        return versionProvider ?: viteInstance::version
    }
```

Replace `templateRendererOrDefault` with:

```kotlin
    internal val templateRendererOrDefault: TemplateRenderer get() {
        return templateRenderer ?: SimpleTemplateRenderer(templatePath, viteInstance)
    }
```

Update the `templateRenderer` KDoc sentence "If left `null`, [SimpleTemplateRenderer] will be used, loading the template specified by [templatePath]." to "If left `null`, [SimpleTemplateRenderer] will be used, loading the template specified by [templatePath] and replacing its Vite placeholders."

- [ ] **Step 6: Update `InertiaPlugin.kt`**

```kotlin
package io.github.inertia4j.ktor

import io.github.inertia4j.core.InertiaRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * The main Ktor Application Plugin for integrating Inertia4J.
 * This plugin initializes the core [InertiaRenderer] based on the provided [InertiaKtorConfiguration],
 * makes the Ktor-specific [InertiaKtorRenderer] available via application attributes,
 * and serves the Vite build output unless disabled.
 */
val Inertia = createApplicationPlugin(
    name = "Inertia",
    createConfiguration = ::InertiaKtorConfiguration
) {
    val coreRenderer = InertiaRenderer(
        pluginConfig.serializerOrDefault,
        pluginConfig.versionProviderOrDefault,
        pluginConfig.templateRendererOrDefault
    )
    application.attributes.put(
        InertiaKtorRenderer.key,
        InertiaKtorRenderer(coreRenderer, pluginConfig)
    )

    val viteConfiguration = pluginConfig.viteConfiguration

    if (viteConfiguration.serveAssets) {
        val viteConfig = pluginConfig.viteInstance.config
        val cacheControl = "public, max-age=${viteConfiguration.cacheMaxAge.inWholeSeconds}, immutable"

        application.routing {
            staticResources(viteConfig.publicPath.removeSuffix("/"), viteConfig.buildDirectory) {
                modify { _, call -> call.response.header(HttpHeaders.CacheControl, cacheControl) }
            }
        }
    }
}
```

- [ ] **Step 7: Run all Ktor tests**

Run: `./gradlew :inertia4j.ktor:test`
Expected: PASS — `ViteKtorTest` and the existing `InertiaKtorTest` (which sets `versionProvider = { "1" }`).

- [ ] **Step 8: Commit**

```bash
git add inertia4j.ktor
git commit -m "Wire Vite integration into the Ktor plugin

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Spring Boot + React example app and CI

**Files:**
- Create: `examples/spring-boot-react/settings.gradle.kts`, `build.gradle.kts`, `.gitignore`, `package.json`, `tsconfig.json`, `vite.config.ts`, `README.md`
- Create: `examples/spring-boot-react/src/main/frontend/main.tsx`, `src/main/frontend/pages/Home.tsx`, `src/main/frontend/pages/About.tsx`
- Create: `examples/spring-boot-react/src/main/java/io/github/inertia4j/examples/springbootreact/ExampleApplication.java`, `PagesController.java`
- Create: `examples/spring-boot-react/src/main/resources/templates/app.html`
- Test: `examples/spring-boot-react/src/test/java/io/github/inertia4j/examples/springbootreact/ExampleApplicationTest.java`
- Modify: `.github/workflows/ci.yml`, `.gitignore`

**Interfaces:**
- Consumes: `io.github.inertia4j:inertia4j.spring-boot-3` through the composite build; `Inertia.render(String, Map)`; placeholders `@ViteReactRefresh@`, `@Vite(...)@`.
- Produces: nothing consumed by other tasks; Task 10 links to its README.

- [ ] **Step 1: Gradle build files**

`examples/spring-boot-react/settings.gradle.kts`:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "spring-boot-react-example"

includeBuild("../..")
```

`examples/spring-boot-react/build.gradle.kts`:

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.12"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.inertia4j:inertia4j.spring-boot-3:1.0.4")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val npmInstall by tasks.registering(Exec::class) {
    commandLine("npm", "ci")
    inputs.file("package-lock.json")
    outputs.dir("node_modules")
}

val npmBuild by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    commandLine("npm", "run", "build")
    inputs.dir("src/main/frontend")
    inputs.files("package.json", "package-lock.json", "tsconfig.json", "vite.config.ts")
    outputs.dir("src/main/resources/static/build")
}

tasks.processResources {
    dependsOn(npmBuild)
}

tasks.test {
    useJUnitPlatform()
}
```

`examples/spring-boot-react/.gitignore`:

```
/src/main/resources/static/build/
/vite.hot
```

In the root `.gitignore`, append under `### JS ###`:

```
vite.hot
```

- [ ] **Step 2: Frontend package**

`examples/spring-boot-react/package.json`:

```json
{
  "name": "inertia4j-spring-boot-react-example",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc --noEmit && vite build"
  }
}
```

Run (from `examples/spring-boot-react`) to add dependencies and generate `package-lock.json`:

```bash
npm install react react-dom @inertiajs/react@^2
npm install -D vite @vitejs/plugin-react typescript @types/react @types/react-dom @types/node
```

Expected: `package.json` gains `dependencies` / `devDependencies`; `package-lock.json` created; `@inertiajs/react` resolves to a 2.x version.

`examples/spring-boot-react/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noEmit": true,
    "skipLibCheck": true,
    "isolatedModules": true,
    "types": ["vite/client", "node"]
  },
  "include": ["src/main/frontend", "vite.config.ts"]
}
```

`examples/spring-boot-react/vite.config.ts`:

```ts
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import fs from 'node:fs'

const hotFile = 'vite.hot'

function inertia4jHotFile(): Plugin {
  return {
    name: 'inertia4j-hot-file',
    apply: 'serve',
    configureServer(server) {
      server.httpServer?.once('listening', () => {
        fs.writeFileSync(hotFile, server.resolvedUrls!.local[0])
      })
      const clean = () => fs.rmSync(hotFile, { force: true })
      process.on('exit', clean)
      process.on('SIGINT', () => process.exit())
      process.on('SIGTERM', () => process.exit())
    },
  }
}

export default defineConfig(({ command }) => ({
  plugins: [react(), inertia4jHotFile()],
  base: command === 'build' ? '/build/' : '/',
  build: {
    manifest: true,
    outDir: 'src/main/resources/static/build',
    emptyOutDir: true,
    rollupOptions: { input: 'src/main/frontend/main.tsx' },
  },
  server: {
    origin: 'http://localhost:5173',
    cors: { origin: 'http://localhost:8080' },
  },
}))
```

- [ ] **Step 3: Frontend sources**

`src/main/frontend/main.tsx`:

```tsx
import 'vite/modulepreload-polyfill'
import type { ComponentType } from 'react'
import { createRoot } from 'react-dom/client'
import { createInertiaApp } from '@inertiajs/react'

const pages = import.meta.glob<{ default: ComponentType<any> }>('./pages/**/*.tsx', { eager: true })

createInertiaApp({
  resolve: (name) => {
    const page = pages[`./pages/${name}.tsx`]

    if (!page) {
      throw new Error(`Unknown page: ${name}`)
    }

    return page
  },
  setup({ el, App, props }) {
    createRoot(el).render(<App {...props} />)
  },
})
```

`src/main/frontend/pages/Home.tsx`:

```tsx
import { Link } from '@inertiajs/react'

type HomeProps = {
  message: string
}

export default function Home({ message }: HomeProps) {
  return (
    <main>
      <h1>{message}</h1>
      <Link href="/about">About</Link>
    </main>
  )
}
```

`src/main/frontend/pages/About.tsx`:

```tsx
import { Link } from '@inertiajs/react'

type AboutProps = {
  springBootVersion: string
}

export default function About({ springBootVersion }: AboutProps) {
  return (
    <main>
      <h1>About</h1>
      <p>Served by Spring Boot {springBootVersion} through Inertia4J.</p>
      <Link href="/">Home</Link>
    </main>
  )
}
```

- [ ] **Step 4: Write the failing backend test**

`src/test/java/io/github/inertia4j/examples/springbootreact/ExampleApplicationTest.java`:

```java
package io.github.inertia4j.examples.springbootreact;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "inertia.vite.hot-file=build/no-dev-server.hot")
@AutoConfigureMockMvc
class ExampleApplicationTest {
    @Autowired
    MockMvc mvc;

    @Test
    void homePage_loadsBuiltViteEntry() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(matchesPattern(
                "(?s).*<script type=\"module\" src=\"/build/assets/main-[\\w-]+\\.js\"></script>.*"
            )));
    }

    @Test
    void aboutPage_rendersAboutComponent() throws Exception {
        mvc.perform(get("/about").header("X-Inertia", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.component").value("About"));
    }
}
```

Run: `./gradlew -p examples/spring-boot-react test`
Expected: FAIL — compilation error, no `@SpringBootConfiguration` found (application class missing).

- [ ] **Step 5: Backend sources**

`src/main/java/io/github/inertia4j/examples/springbootreact/ExampleApplication.java`:

```java
package io.github.inertia4j.examples.springbootreact;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
```

`src/main/java/io/github/inertia4j/examples/springbootreact/PagesController.java`:

```java
package io.github.inertia4j.examples.springbootreact;

import io.github.inertia4j.springboot3.Inertia;
import org.springframework.boot.SpringBootVersion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PagesController {
    private final Inertia inertia;

    public PagesController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GetMapping("/")
    public ResponseEntity<String> home() {
        return inertia.render("Home", Map.of("message", "Hello from Spring Boot"));
    }

    @GetMapping("/about")
    public ResponseEntity<String> about() {
        return inertia.render("About", Map.of("springBootVersion", SpringBootVersion.getVersion()));
    }
}
```

`src/main/resources/templates/app.html`:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Inertia4J</title>
    @ViteReactRefresh@
    @Vite(src/main/frontend/main.tsx)@
  </head>
  <body>
    <div id="app" data-page='@PageObject@'></div>
  </body>
</html>
```

- [ ] **Step 6: Run the example build**

Run: `./gradlew -p examples/spring-boot-react build`
Expected: `npmInstall`, `npmBuild` (tsc + vite build) and tests PASS; `examples/spring-boot-react/src/main/resources/static/build/.vite/manifest.json` exists and is git-ignored (`git status` does not list it).

- [ ] **Step 7: Example README**

`examples/spring-boot-react/README.md`:

````markdown
# Inertia4J — Spring Boot + React example

A Spring Boot 3 application rendering React pages through Inertia4J, bundled by Vite.
It builds against the Inertia4J modules of this repository.

## Development

Run the Vite dev server and the application in two terminals, from this directory:

```bash
npm install
npm run dev
```

```bash
../../gradlew bootRun
```

Open http://localhost:8080. While `npm run dev` runs, it writes `vite.hot`, so pages load scripts from the dev server
with hot module replacement. Stop it and the application falls back to the production build.

## Production

```bash
../../gradlew bootJar
java -jar build/libs/spring-boot-react-example.jar
```

`bootJar` runs `npm ci` and `npm run build`, which writes the bundle and `.vite/manifest.json` to
`src/main/resources/static/build`, packaged in the jar and served under `/build/`.

See [the Vite integration guide](../../docs/vite.md).
````

- [ ] **Step 8: CI job**

Append to `.github/workflows/ci.yml` under `jobs:` (same indentation as `build:`):

```yaml
  example:

    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: examples/spring-boot-react/package-lock.json
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      - name: Build example application
        run: ./gradlew -p examples/spring-boot-react build
```

- [ ] **Step 9: Manual dev-mode check**

In `examples/spring-boot-react`: run `npm run dev` (background), confirm `vite.hot` contains `http://localhost:5173/`, run `../../gradlew bootRun` (background), then:

Run: `curl -s localhost:8080 | grep -E '@vite/client|@react-refresh|main.tsx'`
Expected: three matching lines pointing to `http://localhost:5173`.

Run: `curl -s http://localhost:5173/@vite/client | head -c 100`
Expected: JavaScript (not a 404 page).

Stop both processes; confirm `vite.hot` is deleted.

- [ ] **Step 10: Commit**

```bash
git add .gitignore .github/workflows/ci.yml examples/spring-boot-react
git commit -m "Add Spring Boot + React example using the Vite integration

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Documentation

**Files:**
- Create: `docs/vite.md`
- Modify: `inertia4j.spring-boot-3/README.md`, `inertia4j.spring-boot-4/README.md`, `inertia4j.ktor/README.md`, `docs/advanced.md`, `docs/roadmap.md`

**Interfaces:**
- Consumes: property names from Task 7, Ktor block from Task 8, `vite.config.ts` from Task 9.

- [ ] **Step 1: Write `docs/vite.md`**

````markdown
# Vite integration

Inertia4J follows [Vite's backend integration guide](https://vite.dev/guide/backend-integration.html): the root
template loads your frontend from the Vite dev server while it runs, and from the production build otherwise.
The Inertia asset version is derived from the build, so browsers reload after each deployment.

## Setup

### 1. Configure Vite

`vite.config.ts` at the root of your project (the working directory of the backend):

```ts
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import fs from 'node:fs'

const hotFile = 'vite.hot'

function inertia4jHotFile(): Plugin {
  return {
    name: 'inertia4j-hot-file',
    apply: 'serve',
    configureServer(server) {
      server.httpServer?.once('listening', () => {
        fs.writeFileSync(hotFile, server.resolvedUrls!.local[0])
      })
      const clean = () => fs.rmSync(hotFile, { force: true })
      process.on('exit', clean)
      process.on('SIGINT', () => process.exit())
      process.on('SIGTERM', () => process.exit())
    },
  }
}

export default defineConfig(({ command }) => ({
  plugins: [react(), inertia4jHotFile()],
  base: command === 'build' ? '/build/' : '/',
  build: {
    manifest: true,
    outDir: 'src/main/resources/static/build',
    emptyOutDir: true,
    rollupOptions: { input: 'src/main/frontend/main.tsx' },
  },
  server: {
    origin: 'http://localhost:5173',
    cors: { origin: 'http://localhost:8080' },
  },
}))
```

- `inertia4jHotFile` writes the dev server URL to `vite.hot` while `vite` runs; Inertia4J switches to dev mode
  when the file exists.
- `base` applies to the build only, matching the URL prefix under which Inertia4J serves built files.
- `server.cors.origin` must be the origin of your backend.

### 2. Start the entry with the module preload polyfill

```ts
import 'vite/modulepreload-polyfill'
```

### 3. Reference the entry in the template

`src/main/resources/templates/app.html`:

```html
<head>
  @ViteReactRefresh@
  @Vite(src/main/frontend/main.tsx)@
</head>
<body>
  <div id="app" data-page='@PageObject@'></div>
</body>
```

- `@Vite(a, b)@` renders the tags of one or more entries, given as paths relative to the Vite root.
- `@ViteReactRefresh@` renders the React Fast Refresh preamble in dev mode; omit it for Vue, Svelte and other
  frameworks.

### 4. Ignore generated files

```
vite.hot
/src/main/resources/static/build/
```

## Development

Run `vite` and your application side by side. Pages load `@vite/client` and your entries from the dev server, with
hot module replacement. The asset version is `dev`.

## Production

Run `vite build` before packaging (for example from a Gradle task, as in the
[example application](../examples/spring-boot-react)). Pages load the hashed files listed in
`.vite/manifest.json` with their stylesheets and `modulepreload` hints. Files are served under `/build/` with
`Cache-Control: public, max-age=31536000, immutable`. The asset version is the SHA-256 of the manifest.

## Configuration

### Spring Boot

| Property | Default | Description |
|---|---|---|
| `inertia.vite.enabled` | `true` | Enables placeholders, asset version and asset serving |
| `inertia.vite.hot-file` | `vite.hot` | File enabling dev mode |
| `inertia.vite.build-directory` | `static/build` | Classpath directory of the build output |
| `inertia.vite.manifest` | `{build-directory}/.vite/manifest.json` | Classpath location of the manifest |
| `inertia.vite.public-path` | `/build/` | URL prefix of built files |
| `inertia.vite.cache-max-age` | `365d` | `max-age` of built files |

Defining your own `Vite`, `VersionProvider` or `TemplateRenderer` bean replaces the default one.

### Ktor

```kotlin
install(Inertia) {
    vite {
        hotFile = Path.of("vite.hot")
        buildDirectory = "static/build"
        manifestPath = null // "$buildDirectory/.vite/manifest.json"
        publicPath = "/build/"
        serveAssets = true
        cacheMaxAge = 365.days
    }
}
```

Setting `versionProvider` replaces the Vite asset version.

## Custom template renderers

See [Advanced usage](advanced.md#vite-tags-in-a-custom-renderer).

## Troubleshooting

- **`Vite manifest not found at classpath:...`** — the dev server is not running (no `vite.hot` in the working
  directory) and the frontend was not built. Start `vite`, or run `vite build`.
- **`Unable to locate '...' in the Vite manifest`** — the placeholder entry must match `build.rollupOptions.input`,
  relative to the Vite root.
- **Scripts blocked by CORS in development** — set `server.cors.origin` to the backend origin.
````

- [ ] **Step 2: Update the Spring Boot READMEs**

In both `inertia4j.spring-boot-3/README.md` and `inertia4j.spring-boot-4/README.md`, append to the end of the
"### The HTML Template" section (after the paragraph ending "given the JSON object will use double quotes."):

```markdown

To load your frontend, add `@Vite(src/main/frontend/main.tsx)@` to the template head (preceded by
`@ViteReactRefresh@` for React). It renders the tags of the Vite dev server while it runs and of the production
build otherwise. See the [Vite integration guide](../docs/vite.md).
```

In the "### Asset Versioning" section, replace the paragraph starting "The `VersionProvider` bean is optional, with the default implementation returning a fixed string." (through "**It's highly recommended to provide a custom implementation to prevent this issue**.") with:

```markdown
The `VersionProvider` bean is optional. The default implementation returns the SHA-256 of the Vite manifest, so
clients reload after each deployment of a new frontend build (see the [Vite integration guide](../docs/vite.md)).
Provide your own implementation if you don't build your frontend with Vite.
```

- [ ] **Step 3: Update the Ktor README**

In `inertia4j.ktor/README.md`, append the same "To load your frontend…" paragraph as Step 2 to the end of "### The HTML Template".

In "### Asset Versioning", replace the paragraph starting "The `versionProvider` property is optional, with the default implementation returning a fixed string." (through "**It's highly recommended to provide a custom implementation to prevent this issue**.") with:

```markdown
The `versionProvider` property is optional. By default, the version is the SHA-256 of the Vite manifest, so clients
reload after each deployment of a new frontend build (see the [Vite integration guide](../docs/vite.md)). Set it if
you don't build your frontend with Vite.
```

- [ ] **Step 4: Update `docs/advanced.md`**

Append at the end of the file:

````markdown

### Vite tags in a custom renderer

Custom renderers render the Vite tags through the `Vite` instance, available as a Spring bean or created from a
`ViteConfig`:

```java
import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.spi.TemplateRenderer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class MyCustomTemplateRenderer implements TemplateRenderer {
    private final Vite vite;

    public MyCustomTemplateRenderer(Vite vite) {
        this.vite = vite;
    }

    @Override
    public String render(String pageObjectJson) {
        String head = vite.reactRefreshTag() + vite.tags("src/main/frontend/main.tsx");
        /* ... */
    }
}
```

`vite.version()` returns the matching Inertia asset version.
````

- [ ] **Step 5: Update `docs/roadmap.md`**

Replace the Spring line:

```markdown
    - [x] Setup Vite and [integrate it](https://v3.vitejs.dev/guide/backend-integration.html) with Spring for development
```

with:

```markdown
    - [x] Setup Vite and [integrate it](https://vite.dev/guide/backend-integration.html) with Spring
```

Replace the Ktor line:

```markdown
    - [ ] Setup Vite and [integrate it](https://v3.vitejs.dev/guide/backend-integration.html) with Ktor for development
```

with:

```markdown
    - [x] Setup Vite and [integrate it](https://vite.dev/guide/backend-integration.html) with Ktor
```

Add under `## 1.1`, after "- [x] Merging props (shallow and deep)":

```markdown
- [x] Vite integration: dev server and manifest tags, asset versioning, asset serving ([guide](vite.md))
```

- [ ] **Step 6: Verify the whole build**

Run: `./gradlew test`
Expected: PASS for all modules.

- [ ] **Step 7: Commit**

```bash
git add docs inertia4j.spring-boot-3/README.md inertia4j.spring-boot-4/README.md inertia4j.ktor/README.md
git commit -m "Document the Vite integration

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```
