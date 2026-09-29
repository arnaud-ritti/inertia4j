import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.maven.MavenPom

/**
 * POM metadata shared by every published Inertia4J artifact.
 */
fun MavenPom.inertia4jMetadata() {
    url.set("https://github.com/Inertia4J/inertia4j")

    licenses {
        license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }

    developers {
        developer {
            id.set("edrd-f")
            name.set("Eduardo Fonseca")
        }
        developer {
            id.set("pefcos")
            name.set("Pedro Fronchetti Costa da Silva")
            email.set("pfronchetti@gmail.com")
        }
    }

    scm {
        connection.set("scm:git:https://github.com/Inertia4J/inertia4j.git")
        developerConnection.set("scm:git:ssh://git@github.com:Inertia4J/inertia4j.git")
        url.set("https://github.com/Inertia4J/inertia4j")
    }
}

/**
 * Local repository the artifacts are staged to before JReleaser deploys them.
 */
fun RepositoryHandler.inertia4jStagingDeploy(project: Project) {
    maven {
        url = project.layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
    }
}
