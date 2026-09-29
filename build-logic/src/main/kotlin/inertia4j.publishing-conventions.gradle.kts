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
                url = "https://github.com/Inertia4J/inertia4j"

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                developers {
                    developer {
                        id = "edrd-f"
                        name = "Eduardo Fonseca"
                    }
                    developer {
                        id = "pefcos"
                        name = "Pedro Fronchetti Costa da Silva"
                        email = "pfronchetti@gmail.com"
                    }
                }

                scm {
                    connection = "scm:git:https://github.com/Inertia4J/inertia4j.git"
                    developerConnection = "scm:git:ssh://git@github.com:Inertia4J/inertia4j.git"
                    url = "https://github.com/Inertia4J/inertia4j"
                }
            }
        }
    }

    repositories {
        maven {
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI() // used by JReleaser
        }
    }
}

signing {
    useGpgCmd()
}
