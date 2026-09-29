# Contributing to Inertia4J

Thanks for your interest in Inertia4J. This guide explains how to report problems, propose changes and get a pull
request merged.

By participating in this project you agree to abide by the [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting bugs and requesting features

Search the [issue tracker](https://github.com/arnaud-ritti/inertia4j/issues) first, including closed issues. If nothing
covers your case, open a new issue and pick the template that fits: bug report, feature request or documentation
improvement. A minimal reproduction makes a bug much easier to fix.

Security vulnerabilities must **not** be reported in public issues. See [SECURITY.md](SECURITY.md).

## Before writing code

Comment on the issue you want to work on so others know it is taken, and wait for a maintainer to confirm the approach
for anything larger than a small fix. Pull requests should always reference an issue.

## Development setup

You need:

- **JDK 17 or newer** to run Gradle. The build uses Gradle toolchains: library modules compile for Java 11 and the
  Spring modules for Java 17. Missing JDKs are downloaded automatically through the Foojay resolver.
- **Node.js 20 or newer**. The TypeScript generator tests run `tsc` against the generated files, and the example
  application builds its frontend with Vite.

Clone your fork and run the full build:

```shell
git clone https://github.com/<your-user>/inertia4j.git
cd inertia4j
./gradlew build
```

Useful commands:

| Command | What it does |
|---|---|
| `./gradlew build` | Compiles, tests and assembles every module |
| `./gradlew test` | Runs the test suites only |
| `./gradlew :inertia4j.core:test` | Runs the tests of a single module |
| `./gradlew -p examples/spring-boot-react build` | Builds the Spring Boot + React example |
| `./gradlew publishToMavenLocal` | Installs the artifacts in `~/.m2` for testing |

## Project structure

| Module | Role |
|---|---|
| `inertia4j.spi` | Service provider interfaces users can implement. Changing existing interfaces breaks applications, so treat them as public API. |
| `inertia4j.core` | Framework-agnostic protocol logic shared by every adapter. It must only depend on the SPI and should only gain features that every adapter uses. |
| `inertia4j.spring-shared` | Code shared by both Spring Boot adapters. |
| `inertia4j.spring-boot-3` | Spring Boot 3 adapter and auto-configuration. |
| `inertia4j.spring-boot-4` | Spring Boot 4 adapter and auto-configuration. |
| `inertia4j.ktor` | Ktor plugin. |
| `inertia4j.typescript-annotations` | Annotations that mark props classes for TypeScript generation. |
| `inertia4j.typescript` | Generator that turns props classes into TypeScript declarations. |
| `inertia4j.typescript-gradle-plugin` | Gradle plugin that runs the generator in user builds. |
| `build-logic` | Convention plugins shared by the modules (toolchains, publishing). |
| `examples/` | Sample applications, built in CI against the local modules. |

## Coding guidelines

- Follow the style of the surrounding code. The repository ships an [`.editorconfig`](.editorconfig) that most IDEs
  pick up automatically.
- Keep the public API small. Prefer package-private types and document every public type and method with Javadoc.
- Every behavior change needs a test. Bug fixes should come with a test that fails without the fix.
- Update the relevant documentation (module `README.md`, `docs/`) in the same pull request as the code.
- Avoid new runtime dependencies in `inertia4j.core` and `inertia4j.spi`; users pull them into every application.

## Commits and pull requests

- Keep pull requests focused on a single issue. Unrelated refactorings belong in their own pull request.
- Write commit messages in the imperative mood with a short subject line, for example
  `Add partial reload support to Ktor adapter`.
- Make sure `./gradlew build` passes locally. CI runs the same build on every pull request.
- Fill in the pull request template and link the issue it closes.

A maintainer will review your pull request. Expect questions and change requests; they are part of the process, not a
rejection.

## License

Inertia4J is licensed under the [Apache License 2.0](LICENSE). By submitting a contribution you agree that it is
licensed under the same terms, as described in section 5 of the license.
