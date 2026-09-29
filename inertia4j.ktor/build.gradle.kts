import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("inertia4j.publishing-conventions")
}

kotlin {
    // Compiled with a recent Kotlin, but readable by projects on Kotlin 2.0 and linked against its standard library.
    coreLibrariesVersion = "2.0.21"
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
    }
}

dependencies {
    api(project(":inertia4j.core"))
    api(project(":inertia4j.spi"))

    compileOnly(libs.ktor.client.core)
    compileOnly(libs.ktor.server.core)
    compileOnly(libs.ktor.server.netty)
    compileOnly(libs.ktor.server.sessions)
    compileOnly(libs.jackson2.databind)

    testImplementation(libs.jackson2.databind)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.sessions)
    testImplementation(libs.kotlin.test)
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J Ktor"
        description = "Inertia4J back-end adapter for Ktor"
        inceptionYear = "2025"
    }
}
