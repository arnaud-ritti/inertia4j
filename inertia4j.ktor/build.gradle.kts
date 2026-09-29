plugins {
    alias(libs.plugins.kotlin.jvm)
    id("inertia4j.publishing-conventions")
}

version = "1.0.3"

dependencies {
    api(project(":inertia4j.core"))
    api(project(":inertia4j.spi"))

    compileOnly(libs.ktor.server.core)
    compileOnly(libs.ktor.server.netty)

    testImplementation(libs.jackson2.databind)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J Ktor"
        description = "Inertia4J back-end adapter for Ktor"
        inceptionYear = "2025"
    }
}
