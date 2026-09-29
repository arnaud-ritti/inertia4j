# Asset versioning

After a deployment, open tabs still run the old JavaScript. Inertia detects this with an asset version: every page
object carries the current version, and the client sends it back in `X-Inertia-Version`. When the versions differ on
a `GET` Inertia request, the server answers `409 Conflict` with `X-Inertia-Location`, and the client reloads the page
in full to pick up the new assets. Flash data is kept for that reload.

## Default version

With the [Vite integration](../vite.md), you don't need to do anything:

| Situation                        | Version                            |
|----------------------------------|------------------------------------|
| Vite dev server running          | `dev`                              |
| Production build found           | SHA-256 of `.vite/manifest.json`   |
| Neither                          | `1`                                |

Each `vite build` changes the manifest, so each deployment of a new frontend changes the version.

## Custom version

If you don't build with Vite, provide the version yourself, for example a hash computed at build time.

**Spring Boot.** Define a `VersionProvider` bean (`io.github.inertia4j.springboot3.VersionProvider`, or
`springboot4`):

```java
@Component
public class BuildVersionProvider implements VersionProvider {
    private final String version;

    public BuildVersionProvider(BuildProperties build) {
        this.version = build.getVersion();
    }

    @Override
    public String get() {
        return version;
    }
}
```

**Ktor.** Set `versionProvider` in the plugin configuration:

```kotlin
private val assetVersion = object {}.javaClass.getResource("/assets.sha1")?.readText()
    ?: error("Missing asset digest file")

install(Inertia) {
    versionProvider = { assetVersion }
}
```

The provider is called on every request, so keep it cheap: compute the version once and return the cached value.

See the [Inertia documentation](https://inertiajs.com/docs/v3/advanced/asset-versioning).
