# Installation

Inertia4J is published to [GitHub Packages](https://github.com/arnaud-ritti/inertia4j/packages) under the
`dev.arkoder` group. GitHub Packages requires authentication to download packages, even public ones, so your build
needs the repository URL and a token.

## Create a token

Create a [personal access token (classic)](https://github.com/settings/tokens/new?scopes=read:packages) with the
`read:packages` scope. Keep it out of your repository: store it in your user-level Gradle or Maven settings locally,
and as a secret in CI.

## Gradle

Store the credentials in `~/.gradle/gradle.properties`:

```properties
gpr.user=your-github-username
gpr.key=ghp_yourtoken
```

Add the repository next to Maven Central in `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "Inertia4J"
        url = uri("https://maven.pkg.github.com/arnaud-ritti/inertia4j")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GPR_USER")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GPR_TOKEN")
        }
    }
}

dependencies {
    implementation("dev.arkoder:inertia4j-spring-boot-3:3.0.0")
}
```

### TypeScript Gradle plugin

The [TypeScript plugin](guides/typescript.md) is resolved from the same repository. Declare it in the
`pluginManagement` block of `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://maven.pkg.github.com/arnaud-ritti/inertia4j")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GPR_USER")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GPR_TOKEN")
            }
        }
    }
}
```

## Maven

Store the credentials in `~/.m2/settings.xml`, under a server id you reuse in the POM:

```xml
<settings>
    <servers>
        <server>
            <id>inertia4j</id>
            <username>your-github-username</username>
            <password>ghp_yourtoken</password>
        </server>
    </servers>
</settings>
```

Declare the repository in `pom.xml`:

```xml
<repositories>
    <repository>
        <id>inertia4j</id>
        <url>https://maven.pkg.github.com/arnaud-ritti/inertia4j</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>dev.arkoder</groupId>
        <artifactId>inertia4j-spring-boot-3</artifactId>
        <version>3.0.0</version>
    </dependency>
</dependencies>
```

The [Maven guide](guides/maven.md) covers the rest of a Maven build.

## CI

Store the username and token as repository secrets (`GPR_USER`, `GPR_TOKEN`) and expose them to the build. For
GitHub Actions with Gradle:

```yaml
- name: Build
  run: ./gradlew build
  env:
    GPR_USER: ${{ secrets.GPR_USER }}
    GPR_TOKEN: ${{ secrets.GPR_TOKEN }}
```

With Maven, `actions/setup-java` writes the `settings.xml` server entry for you:

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: 17
    server-id: inertia4j
    server-username: GPR_USER
    server-password: GPR_TOKEN

- name: Build
  run: ./mvnw -B verify
  env:
    GPR_USER: ${{ secrets.GPR_USER }}
    GPR_TOKEN: ${{ secrets.GPR_TOKEN }}
```

## Artifacts

| Artifact                                     | Content                                         |
|----------------------------------------------|-------------------------------------------------|
| `dev.arkoder:inertia4j-spring-boot-3`        | Spring Boot 3 adapter                           |
| `dev.arkoder:inertia4j-spring-boot-4`        | Spring Boot 4 adapter                           |
| `dev.arkoder:inertia4j-ktor`                 | Ktor plugin                                     |
| `dev.arkoder:inertia4j-core`                 | Framework-agnostic core, for custom adapters    |
| `dev.arkoder:inertia4j-spi`                  | Extension interfaces                            |
| `dev.arkoder:inertia4j-typescript-annotations` | `@InertiaPage`, `@InertiaShared`, `@InertiaForm` |
| `dev.arkoder:inertia4j-typescript`           | TypeScript generator (used by Maven builds)     |
| `dev.arkoder.inertia4j.typescript` (plugin)  | Gradle plugin generating TypeScript types       |

The adapters bring `core`, `spi` and the annotations transitively.
