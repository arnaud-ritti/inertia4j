plugins {
    id("inertia4j.spring-conventions")
}

dependencies {
    api(project(":inertia4j.core"))
    api(project(":inertia4j.spi"))

    compileOnly("org.springframework:spring-test")
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.boot3.dependencies.get().toString())
    }
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J Shared"
        description = "Inertia4J shared logic with Spring Boot 3 and Spring Boot 4 adapters"
        inceptionYear = "2026"
    }
}
