plugins {
    alias(libs.plugins.kotlin.jvm)
    id("inertia4j.publishing-conventions")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
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
        description = "Generates TypeScript types for Inertia4J props classes"
        inceptionYear = "2026"
    }
}
