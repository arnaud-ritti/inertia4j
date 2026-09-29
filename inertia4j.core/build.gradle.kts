plugins {
    id("inertia4j.publishing-conventions")
}

version = "2.0.0"

dependencies {
    implementation(project(":inertia4j.spi"))

    compileOnly(libs.jackson2.databind)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.jackson2.databind)
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J Core"
        description = "Inertia4J back-end adapter core"
        inceptionYear = "2025"
    }
}
