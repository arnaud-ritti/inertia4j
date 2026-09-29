plugins {
    id("inertia4j.publishing-conventions")
}

version = "2.0.0"

dependencies {
    api(project(":inertia4j.typescript-annotations"))
    implementation(project(":inertia4j.spi"))

    compileOnly(libs.jackson2.databind)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jackson2.databind)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<JavaCompile>("compileTestJava") {
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named<Test>("test") {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J Core"
        description = "Inertia4J back-end adapter core"
        inceptionYear = "2025"
    }
}
