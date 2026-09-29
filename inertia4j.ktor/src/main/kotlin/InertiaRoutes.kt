package dev.arkoder.inertia4j.ktor

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.nio.file.Files
import java.nio.file.Path

private val RouteNameKey = AttributeKey<String>("InertiaRouteName")
internal val InternalRouteKey = AttributeKey<Boolean>("InertiaInternalRoute")
private val AnyMethod = listOf("get", "post", "put", "patch", "delete")

/**
 * Names the route in the route helpers generated for the frontend, e.g. `users.show` becomes `users.show({ id })`.
 * Unnamed routes get a name derived from their path and method.
 *
 * @param name dot-separated route name.
 * @return this route.
 */
fun Route.named(name: String): Route {
    attributes.put(RouteNameKey, name)

    return this
}

/**
 * Describes the routes of the application as JSON, read by the Inertia4J route generator. Routes using wildcards or
 * regular expressions are left out, since no URL can be built for them, and so are the routes of the Inertia plugin.
 *
 * @return the route manifest.
 */
fun Application.inertiaRouteManifest(): String {
    val root = pluginOrNull(RoutingRoot) ?: return manifestJson(emptyList())
    val routes = root.getAllRoutes().mapNotNull(::manifestRoute).distinct()

    return manifestJson(routes)
}

/**
 * Writes [inertiaRouteManifest] to a file, creating its parent directories.
 *
 * @param file manifest file.
 */
fun Application.writeInertiaRouteManifest(file: Path) {
    file.toAbsolutePath().parent?.let(Files::createDirectories)
    Files.writeString(file, inertiaRouteManifest())
}

private data class ManifestRoute(val name: String?, val methods: List<String>, val path: String)

private fun manifestRoute(leaf: RoutingNode): ManifestRoute? {
    val segments = mutableListOf<String>()
    var method: String? = null
    var name: String? = null
    var node: RoutingNode? = leaf

    while (node != null) {
        if (node.attributes.getOrNull(InternalRouteKey) == true) {
            return null
        }

        if (name == null) {
            name = node.attributes.getOrNull(RouteNameKey)
        }

        when (val selector = node.selector) {
            is HttpMethodRouteSelector -> method = method ?: selector.method.value.lowercase()

            is PathSegmentConstantRouteSelector -> segments.add(selector.value)

            is PathSegmentParameterRouteSelector -> segments.add(
                "${selector.prefix.orEmpty()}{${selector.name}}${selector.suffix.orEmpty()}",
            )

            is PathSegmentOptionalParameterRouteSelector ->
                segments.add("${selector.prefix.orEmpty()}{${selector.name}?}${selector.suffix.orEmpty()}")

            is PathSegmentTailcardRouteSelector -> segments.add("${selector.prefix}{*${selector.name.ifEmpty { "path" }}}")

            is PathSegmentWildcardRouteSelector, is PathSegmentRegexRouteSelector -> return null

            else -> Unit
        }

        node = node.parent as? RoutingNode
    }

    val path = "/" + segments.reversed().joinToString("/")
    val methods = method?.let(::listOf) ?: AnyMethod

    return ManifestRoute(name, methods, path)
}

private fun manifestJson(routes: List<ManifestRoute>): String {
    val entries = routes.joinToString(",\n") { route ->
        val name = route.name?.let(::jsonString) ?: "null"
        val methods = route.methods.joinToString(", ", transform = ::jsonString)

        """    { "name": $name, "methods": [$methods], "path": ${jsonString(route.path)} }"""
    }

    return if (routes.isEmpty()) {
        "{\n  \"version\": 1,\n  \"routes\": []\n}\n"
    } else {
        "{\n  \"version\": 1,\n  \"routes\": [\n$entries\n  ]\n}\n"
    }
}

private fun jsonString(value: String): String {
    val escaped = buildString {
        value.forEach { character ->
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character < ' ' -> append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
    }

    return "\"$escaped\""
}
