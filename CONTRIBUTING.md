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
- Update the matching guide in [`docs/`](docs/README.md) in the same pull request as the code, with a Java and a Kotlin
  example when the feature exists in both adapters.
- Avoid new runtime dependencies in `inertia4j.core` and `inertia4j.spi`; users pull them into every application.

## Commits and pull requests

- Keep pull requests focused on a single issue. Unrelated refactorings belong in their own pull request.
- Write commit messages in the imperative mood with a short subject line, for example
  `Add partial reload support to Ktor adapter`.
- Make sure `./gradlew build` passes locally. CI runs the same build on every pull request.
- Fill in the pull request template and link the issue it closes.

A maintainer will review your pull request. Expect questions and change requests; they are part of the process, not a
rejection.

## Versioning and releases

The major version follows the Inertia.js protocol: 3.x implements Inertia.js v3. Minor and patch versions follow
[semantic versioning](https://semver.org/) within that line. The build version is `3.0.0-SNAPSHOT` in
`gradle.properties`; release versions come from the git tag.

Each major version has its own branch, named after it: `3.x` holds the Inertia.js v3 line and is the default branch.
Open pull requests against it.

Maintainers release by pushing a tag from that branch:

```shell
git tag -a v3.1.0 -m "v3.1.0"
git push origin v3.1.0
```

The [release workflow](.github/workflows/release.yml) builds and tests that commit, publishes every module to GitHub
Packages with the tag's version, and creates a GitHub release with generated notes. Tags with a suffix, such as
`v3.1.0-rc.1`, are marked as pre-releases. A version can't be published twice, so fix a broken release with a new
patch version.

## License

Inertia4J is licensed under the [Apache License 2.0](LICENSE). By submitting a contribution you agree that it is
licensed under the same terms, as described in section 5 of the license.
