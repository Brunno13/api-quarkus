import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.allopen") version "2.4.10"
    id("io.quarkus")
    jacoco
    id("dev.detekt") version "2.0.0-alpha.6"
    id("com.diffplug.spotless") version "8.10.4"
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId = providers.gradleProperty("quarkusPlatformGroupId").get()
val quarkusPlatformArtifactId = providers.gradleProperty("quarkusPlatformArtifactId").get()
val quarkusPlatformVersion = providers.gradleProperty("quarkusPlatformVersion").get()

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))

    implementation("io.quarkus:quarkus-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-smallrye-openapi")

    implementation("io.quarkus:quarkus-mongodb-panache-kotlin")

    implementation("io.quarkus:quarkus-smallrye-jwt")
    implementation("io.quarkus:quarkus-smallrye-jwt-build")
    implementation("io.quarkus:quarkus-elytron-security-common")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-logging-json")

    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-test-security")
    testImplementation("io.quarkus:quarkus-jacoco")
}

// AQK-5: preserve audited legacy findings; fail the build on new findings.
detekt {
    ignoreFailures = false
    baseline = file("config/detekt/baseline.xml")
}

// Match the JaCoCo version used by the Quarkus 3.40 coverage extension.
jacoco {
    toolVersion = "0.8.15"
}

group = "br.com.brunno"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        javaParameters = true
    }
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty(
        "java.util.logging.manager",
        "org.jboss.logmanager.LogManager"
    )
}

// Default quality gate: no MongoDB required.
tasks.named<Test>("test") {
    exclude("**/*IntegrationTest.class")

    useJUnitPlatform {
        excludeTags("mongodb-integration")
    }

    // Keep test coverage files inside build/ so `clean` removes old data.
    systemProperty(
        "quarkus.jacoco.data-file",
        layout.buildDirectory.file("jacoco-quarkus.exec").get().asFile.absolutePath
    )
    systemProperty(
        "quarkus.jacoco.report-location",
        layout.buildDirectory.dir("jacoco-report").get().asFile.absolutePath
    )
    systemProperty("quarkus.jacoco.reuse-data-file", "true")

    // Quarkus instruments @QuarkusTest via its own classloader. The Gradle
    // agent covers plain JUnit tests, using the same execution data file.
    extensions.configure<JacocoTaskExtension> {
        excludeClassLoaders = listOf("*QuarkusClassLoader")
        destinationFile = layout.buildDirectory.file("jacoco-quarkus.exec").get().asFile
    }
}

// Quarkus generates the combined report. Do not invoke Gradle's duplicate reporter.
tasks.named("jacocoTestReport") {
    enabled = false
}

// AQK-5: use the same merged execution data as the Quarkus JaCoCo report.
// These minimums are based on the audited 2026-11-17 baseline:
// LINE 608/807 = 75.34%; BRANCH 223/326 = 68.40%.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    executionData.setFrom(layout.buildDirectory.file("jacoco-quarkus.exec"))

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.68".toBigDecimal()
            }
        }
    }
}

// Gradle does not attach JaCoCo coverage verification to check automatically.
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// Explicit MongoDB integration test task.
tasks.register<Test>("mongoIntegrationTest") {
    group = "verification"
    description = "Runs MongoDB integration tests against a dedicated database"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    include("**/*IntegrationTest.class")

    useJUnitPlatform {
        includeTags("mongodb-integration")
    }

    outputs.upToDateWhen { false }
}

// AQK-17: lock application and test dependencies for security scanning.
configurations.configureEach {
    if (name == "runtimeClasspath" || name == "testRuntimeClasspath") {
        resolutionStrategy.activateDependencyLocking()
    }
}

// AQK-16 discovery: inspect Kotlin formatting without changing the existing check gate.
spotless {
    isEnforceCheck = false
    kotlin {
        target("src/main/kotlin/**/*.kt", "src/test/kotlin/**/*.kt")
        ktlint("1.8.0")
    }
}
