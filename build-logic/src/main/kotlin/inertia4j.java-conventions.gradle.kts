plugins {
    `java-library`
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
