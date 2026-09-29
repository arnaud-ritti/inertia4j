plugins {
    id("inertia4j.spring-conventions")
}

version = "2.0.0"

dependencies {
    api(project(":inertia4j.spring-shared"))

    compileOnly(libs.jackson3.databind)
    compileOnly("org.springframework.boot:spring-boot-health")

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.jackson3.databind)
    testImplementation("org.springframework.boot:spring-boot-health")
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.boot4.dependencies.get().toString())
    }
}

publishing.publications.named<MavenPublication>("mavenJava") {
    pom {
        name = "Inertia4J Spring Boot 4"
        description = "Inertia4J back-end adapter for Spring Boot 4"
        inceptionYear = "2026"
    }
}
