pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "inertia4j"

include("inertia4j.typescript-annotations")
include("inertia4j.typescript")
include("inertia4j.spi")
include("inertia4j.core")
include("inertia4j.ktor")
include("inertia4j.spring-shared")
include("inertia4j.spring-boot-3")
include("inertia4j.spring-boot-4")
