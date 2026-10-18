plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    // Generates the no-argument constructors JPA needs to instantiate entities.
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.example"
version = "0.0.1-SNAPSHOT"
description = "task-manager-demo"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("com.h2database:h2")
    // Serves the H2 web console; in Spring Boot 4 it is its own module.
    runtimeOnly("org.springframework.boot:spring-boot-h2console")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Kotlin classes are final by default, but Hibernate needs to subclass entities
// to create lazy proxies. kotlin("plugin.spring") already opens Spring-annotated
// classes; this extends the same treatment to JPA annotations.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// The learning exercises in src/test/.../exercises are meant to be red until
// you implement them, so they are kept out of the normal test run. That way
// `./gradlew test` stays an honest signal about the application itself.
tasks.test {
    filter { excludeTestsMatching("*.exercises.*") }
}

tasks.register<Test>("exercises") {
    group = "verification"
    description = "Runs the learning exercises. Red is the starting state — see docs/learning/."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("*.exercises.*") }
    outputs.upToDateWhen { false }   // always re-run, even when nothing changed
}
