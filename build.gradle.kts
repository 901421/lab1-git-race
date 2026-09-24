import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin.jpa)
}

group = "es.unizar.webeng"
version = "2026-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    val springBootVersion = libs.versions.springBoot.get()
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    developmentOnly(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.bootstrap)
    implementation(libs.webjars.locator.lite)
    runtimeOnly(libs.kotlin.reflect)
    runtimeOnly(libs.h2)
    developmentOnly(libs.spring.boot.devtools)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.restclient)
    testImplementation(libs.spring.boot.resttestclient)
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Tests use a throwaway in-memory database, never the file in ./data
    systemProperty("spring.datasource.url", "jdbc:h2:mem:testdb")
}

tasks.withType<BootRun> {
    sourceResources(sourceSets["main"])
}
