plugins {
    id("inertia4j.java-conventions")
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            artifactId = project.name.replace('.', '-')

            pom {
                inertia4jMetadata()
            }
        }
    }

    repositories {
        inertia4jGitHubPackages(project)
    }
}
