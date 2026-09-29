plugins {
    `java-library`
    id("inertia4j.lint-conventions")
}

group = "dev.arkoder"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }

    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// `-PtestJavaVersion=21` runs the test suites on that JDK, whatever toolchain the module compiles with. The Gradle
// plugin is left out: its tests run a Gradle build in the test JVM, so they are bound to the JDKs Gradle supports.
val testJavaVersion = providers.gradleProperty("testJavaVersion").map(String::toInt)

afterEvaluate {
    if (testJavaVersion.isPresent && !plugins.hasPlugin("java-gradle-plugin")) {
        tasks.withType<Test>().configureEach {
            javaLauncher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(testJavaVersion.get())
            }
        }
    }
}
