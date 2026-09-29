plugins {
    id("inertia4j.publishing-conventions")
}

version = "1.0.2"

dependencies {
    api(libs.jspecify)
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J SPI"
        description = "Inertia4J back-end adapter SPIs"
        inceptionYear = "2025"
    }
}
