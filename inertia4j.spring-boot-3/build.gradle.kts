plugins {
    id("inertia4j.spring-conventions")
}

version = "2.0.0"

dependencies {
    api(project(":inertia4j.spring-shared"))

    compileOnly("org.springframework.boot:spring-boot-actuator")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure")
    compileOnly("com.fasterxml.jackson.core:jackson-annotations")

    testImplementation("org.springframework.boot:spring-boot-actuator")
    testImplementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
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
