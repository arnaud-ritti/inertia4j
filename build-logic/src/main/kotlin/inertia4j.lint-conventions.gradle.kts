plugins {
    id("com.diffplug.spotless")
}

spotless {
    java {
        target("src/**/*.java")
        removeUnusedImports()
        forbidWildcardImports()
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
            .setEditorConfigPath(rootProject.file(".editorconfig"))
            .editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea"))
    }
}

plugins.withId("org.jetbrains.kotlin.jvm") {
    spotless {
        kotlin {
            target("src/**/*.kt")
            ktlint()
            .setEditorConfigPath(rootProject.file(".editorconfig"))
            .editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea"))
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Exceptions and events of the library are never serialized, so serialVersionUID warnings are noise.
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
}
