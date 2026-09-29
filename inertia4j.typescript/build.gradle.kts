plugins {
    alias(libs.plugins.kotlin.jvm)
    id("inertia4j.publishing-conventions")
}

version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    api(project(":inertia4j.core"))

    implementation(libs.kotlin.metadata.jvm)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jackson2.databind)
    testImplementation(libs.jspecify)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    val testRuntimeClasspath = sourceSets["test"].runtimeClasspath
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dinertia4j.test.classpath=${testRuntimeClasspath.asPath}")
    })
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J TypeScript"
        description = "Generates TypeScript types for Inertia4J props classes"
        inceptionYear = "2026"
    }
}
