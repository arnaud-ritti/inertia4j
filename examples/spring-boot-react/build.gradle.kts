plugins {
    java
    id("org.springframework.boot") version "3.3.12"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.inertia4j:inertia4j.spring-boot-3:2.0.0")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val npmInstall by tasks.registering(Exec::class) {
    commandLine("npm", "ci")
    inputs.file("package-lock.json")
    outputs.dir("node_modules")
}

val npmBuild by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    commandLine("npm", "run", "build")
    inputs.dir("src/main/frontend")
    inputs.files("package.json", "package-lock.json", "tsconfig.json", "vite.config.ts")
    outputs.dir("src/main/resources/static/build")
    outputs.dir("build/ssr")
}

tasks.processResources {
    dependsOn(npmBuild)
}

tasks.test {
    useJUnitPlatform()
}
