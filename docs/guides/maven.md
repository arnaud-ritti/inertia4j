# Using Inertia4J with Maven

Inertia4J is built with Gradle, but its artifacts are plain Maven artifacts, published to GitHub Packages: first
add that repository and your token as described in [Installation](../installation.md#maven). This guide covers what differs for a Maven
build: declaring the dependencies, building the frontend during the Maven lifecycle, and generating TypeScript types
without the Gradle plugin.

## Dependencies

### Spring Boot

With the Spring Boot parent, only the Inertia4J version needs to be set:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.12</version>
    <relativePath/>
</parent>

<properties>
    <java.version>17</java.version>
    <inertia4j.version>3.0.0</inertia4j.version>
</properties>

<dependencies>
    <dependency>
        <groupId>dev.arkoder</groupId>
        <artifactId>inertia4j-spring-boot-3</artifactId> <!-- inertia4j-spring-boot-4 for Boot 4 -->
        <version>${inertia4j.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

`spring-boot-starter-test` also brings `spring-test`, which the [MockMvc assertions](testing.md) need.

### Ktor

Maven doesn't read Gradle module metadata, so Ktor artifacts must be declared with their `-jvm` suffix:

```xml
<properties>
    <ktor.version>3.0.0</ktor.version>
    <inertia4j.version>3.0.0</inertia4j.version>
</properties>

<dependencies>
    <dependency>
        <groupId>dev.arkoder</groupId>
        <artifactId>inertia4j-ktor</artifactId>
        <version>${inertia4j.version}</version>
    </dependency>
    <dependency>
        <groupId>io.ktor</groupId>
        <artifactId>ktor-server-netty-jvm</artifactId>
        <version>${ktor.version}</version>
    </dependency>
    <!-- Flash data and validation errors -->
    <dependency>
        <groupId>io.ktor</groupId>
        <artifactId>ktor-server-sessions-jvm</artifactId>
        <version>${ktor.version}</version>
    </dependency>
    <!-- Default JSON serializer -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.17.2</version>
    </dependency>

    <dependency>
        <groupId>io.ktor</groupId>
        <artifactId>ktor-server-test-host-jvm</artifactId>
        <version>${ktor.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Compile Kotlin sources with the [`kotlin-maven-plugin`](https://kotlinlang.org/docs/maven.html).

## Building the frontend

The production build has to run before resources are copied, so the manifest and the hashed files end up in the jar.
Bind `npm ci` and `npm run build` to the `generate-resources` phase with the `exec-maven-plugin`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <version>3.5.0</version>
            <executions>
                <execution>
                    <id>npm-ci</id>
                    <phase>generate-resources</phase>
                    <goals>
                        <goal>exec</goal>
                    </goals>
                    <configuration>
                        <executable>npm</executable>
                        <arguments>
                            <argument>ci</argument>
                        </arguments>
                    </configuration>
                </execution>
                <execution>
                    <id>npm-build</id>
                    <phase>generate-resources</phase>
                    <goals>
                        <goal>exec</goal>
                    </goals>
                    <configuration>
                        <executable>npm</executable>
                        <arguments>
                            <argument>run</argument>
                            <argument>build</argument>
                        </arguments>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

This uses the Node.js installed on the machine. To have Maven download Node.js itself, for CI agents without it, use
the [`frontend-maven-plugin`](https://github.com/eirslett/frontend-maven-plugin) instead, with the same phases.

With the [Vite configuration](vite.md#1-configure-vite) of the Vite guide, `vite build` writes to
`src/main/resources/static/build`, which Maven then copies to `target/classes/static/build`. The SSR bundle
(`vite build --ssr`) is run by Node.js next to the application, not served, so keep it out of `src/main/resources`.

## Development

Run Vite and the application side by side:

```shell
npx vite                                  # terminal 1: writes vite.hot
./mvnw spring-boot:run -Dexec.skip=true   # terminal 2
```

`spring-boot:run` runs from the project directory, where Vite writes `vite.hot`, so pages load from the dev server
with hot module replacement. It also runs the lifecycle up to `test-compile`, including the `npm` executions;
`-Dexec.skip=true` skips them, since the dev server replaces the production build.

The commands use the [Maven wrapper](https://maven.apache.org/wrapper/); use `mvn` if your project doesn't have one.

## TypeScript types

The `dev.arkoder.inertia4j.typescript` plugin is Gradle-only. With Maven, call the generator from a small class run by
the `exec-maven-plugin`. Add the generator as a test dependency, so it stays out of your application:

```xml
<dependency>
    <groupId>dev.arkoder</groupId>
    <artifactId>inertia4j-typescript</artifactId>
    <version>${inertia4j.version}</version>
    <scope>test</scope>
</dependency>
```

Create `src/test/java/com/example/app/GenerateInertiaTypes.java`. It scans the classpath it runs with, writes the
declaration file, or, with `--check`, fails when the file is out of date:

```java
package com.example.app;

import dev.arkoder.inertia4j.core.PropertyNaming;
import dev.arkoder.inertia4j.typescript.ErrorValueType;
import dev.arkoder.inertia4j.typescript.GenerationResult;
import dev.arkoder.inertia4j.typescript.GeneratorOptions;
import dev.arkoder.inertia4j.typescript.TypeScriptGenerator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GenerateInertiaTypes {
    private static final Path OutputFile = Path.of("src/main/frontend/types/inertia.d.ts");

    public static void main(String[] args) throws Exception {
        GeneratorOptions options = new GeneratorOptions(
            List.of("com.example.app"), // packages to scan
            PropertyNaming.Camel,
            ErrorValueType.String,
            false                       // nullableByDefault
        );

        List<Path> classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .collect(Collectors.toList());

        GenerationResult result = TypeScriptGenerator.generate(options, classpath);

        result.warnings().forEach(warning -> System.err.println("Inertia types: " + warning));

        if (Arrays.asList(args).contains("--check")) {
            String current = Files.exists(OutputFile) ? Files.readString(OutputFile) : "";

            if (!current.equals(result.content())) {
                System.err.println(OutputFile + " is out of date. Run: ./mvnw test-compile exec:exec@generate-inertia-types");
                System.exit(1);
            }

            return;
        }

        Files.createDirectories(OutputFile.getParent());
        Files.writeString(OutputFile, result.content());
        System.out.println("Wrote " + OutputFile);
    }
}
```

The options match those of the [Gradle plugin](typescript.md#2-generate-the-types-with-gradle). Then add two
executions to the `exec-maven-plugin`. `<classpath/>` expands to the test classpath, which holds your compiled
classes, their dependencies and the generator:

```xml
<execution>
    <id>generate-inertia-types</id>
    <goals>
        <goal>exec</goal>
    </goals>
    <configuration>
        <executable>java</executable>
        <classpathScope>test</classpathScope>
        <arguments>
            <argument>-classpath</argument>
            <classpath/>
            <argument>com.example.app.GenerateInertiaTypes</argument>
        </arguments>
    </configuration>
</execution>
<execution>
    <id>check-inertia-types</id>
    <phase>process-test-classes</phase>
    <goals>
        <goal>exec</goal>
    </goals>
    <configuration>
        <executable>java</executable>
        <classpathScope>test</classpathScope>
        <arguments>
            <argument>-classpath</argument>
            <classpath/>
            <argument>com.example.app.GenerateInertiaTypes</argument>
            <argument>--check</argument>
        </arguments>
    </configuration>
</execution>
```

- `./mvnw test-compile exec:exec@generate-inertia-types` writes the file.
- `./mvnw verify` fails when the file is out of date, so CI catches props changed without regenerating the types.

Only classes under the scanned packages get a declaration. Keep test classes out of those packages if they are
annotated with `@InertiaPage`, `@InertiaShared` or `@InertiaForm`, or they will be picked up too.

## Packaging

`./mvnw package` runs the frontend build, checks the types, runs the tests and produces an executable jar with the
built assets:

```shell
./mvnw package
java -jar target/app-0.0.1-SNAPSHOT.jar
```
