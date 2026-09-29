plugins {
    id("inertia4j.java-conventions")
    `maven-publish`
    signing
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
        inertia4jStagingDeploy(project) // used by JReleaser
    }
}

signing {
    useGpgCmd()
}
