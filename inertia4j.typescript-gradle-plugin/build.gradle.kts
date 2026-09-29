plugins {
    id("inertia4j.java-conventions")
    `java-gradle-plugin`
    `maven-publish`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(project(":inertia4j.typescript"))

    testImplementation(project(":inertia4j.typescript-annotations"))
    testImplementation(libs.junit.jupiter)
    testImplementation(platform(libs.spring.boot3.dependencies))
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework:spring-context")
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("inertiaTypes") {
            id = "dev.arkoder.inertia4j.typescript"
            implementationClass = "dev.arkoder.inertia4j.typescript.gradle.InertiaTypesPlugin"
            displayName = "Inertia4J TypeScript types"
            description = "Generates TypeScript types and route helpers for Inertia4J applications"
        }
    }
}

// java-gradle-plugin creates the `pluginMaven` publication and one marker publication per plugin.
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name = "Inertia4J TypeScript Gradle plugin"
            description = "Gradle plugin generating TypeScript types and route helpers for Inertia4J applications"
            inceptionYear = "2026"
            inertia4jMetadata()
        }
    }

    publications.withType<MavenPublication>().matching { it.name == "pluginMaven" }.configureEach {
        artifactId = project.name.replace('.', '-')
    }

    repositories {
        inertia4jGitHubPackages(project)
    }
}
