# Inertia4J for Spring Boot 3

Inertia.js adapter for Spring Boot 3 (Spring MVC, Java 17+). For a complete application, see the [Spring Boot + React example](../examples/spring-boot-react).

## Installation

Artifacts are published to GitHub Packages; add the repository first, see
[Installation](../docs/installation.md).

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.arkoder:inertia4j-spring-boot-3:3.0.0")
}
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>dev.arkoder</groupId>
    <artifactId>inertia4j-spring-boot-3</artifactId>
    <version>3.0.0</version>
</dependency>
```

The module is auto-configured. It registers:

- the `Inertia` bean (`dev.arkoder.inertia4j.springboot3.Inertia`) used by controllers;
- the `InertiaFilter`, which applies the protocol to every request;
- the Vite integration, serving the frontend build under `/build/`;
- the `inertiaSsr` health indicator when Spring Boot Actuator and SSR are enabled.

## Usage

```java
@RestController
public class UsersController {
    private final Inertia inertia;

    public UsersController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GetMapping("/users")
    public ResponseEntity<String> index() {
        return inertia.render("Users/Index", Map.of(
            "users", users.findAll(),
            "permissions", Inertia.defer(() -> permissions.findAll())
        ));
    }

    @PostMapping("/users")
    public ResponseEntity<String> store(@Valid @ModelAttribute UserForm form, BindingResult result) {
        if (result.hasErrors()) {
            inertia.errors(result);
            return inertia.back();
        }

        users.create(form);
        inertia.flash("message", "User created");
        return inertia.redirect("/users");
    }
}
```

## Documentation

- [Getting started](../docs/getting-started.md)
- Guides: [responses and templates](../docs/guides/responses.md), [redirects](../docs/guides/redirects.md),
  [props](../docs/guides/props.md), [shared data](../docs/guides/shared-data.md),
  [forms and validation](../docs/guides/forms-and-validation.md), [asset versioning](../docs/guides/asset-versioning.md),
  [server-side rendering](../docs/guides/ssr.md), [testing](../docs/guides/testing.md)
- [Vite integration](../docs/guides/vite.md), [TypeScript types](../docs/guides/typescript.md) and [Maven builds](../docs/guides/maven.md)
- [Configuration reference](../docs/reference/configuration.md#spring-boot-properties)
