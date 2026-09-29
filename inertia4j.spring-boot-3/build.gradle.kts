plugins {
    id("inertia4j.spring-conventions")
}

version = "2.0.0"

dependencies {
    api(project(":inertia4j.spring-shared"))
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.boot3.dependencies.get().toString())
    }
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J Spring Boot 3"
        description = "Inertia4J back-end adapter for Spring Boot 3"
        inceptionYear = "2025"
    }
}
