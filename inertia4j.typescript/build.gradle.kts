import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("inertia4j.publishing-conventions")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
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

    implementation(libs.jackson2.databind)
    implementation(libs.kotlin.metadata.jvm)

    testImplementation(project(":inertia4j.spi"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jspecify)
    testImplementation(platform(libs.spring.boot3.dependencies))
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework:spring-context")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    val testRuntimeClasspath = sourceSets["test"].runtimeClasspath
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-Dinertia4j.test.classpath=${testRuntimeClasspath.asPath}")
        },
    )
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J TypeScript"
        description = "Generates TypeScript types and route helpers for Inertia4J applications"
        inceptionYear = "2026"
    }
}
