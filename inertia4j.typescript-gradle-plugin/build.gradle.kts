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
