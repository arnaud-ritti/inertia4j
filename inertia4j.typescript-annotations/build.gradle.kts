plugins {
    id("inertia4j.publishing-conventions")
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J TypeScript Annotations"
        description = "Annotations describing Inertia4J props classes for TypeScript generation"
        inceptionYear = "2026"
    }
}
