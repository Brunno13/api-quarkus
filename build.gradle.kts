import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.w3c.dom.Element
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

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

// AQK-16: isolated PMD CPD toolchain; never added to application/test runtime.
val aqk16Cpd = configurations.create("aqk16Cpd") {
    isCanBeConsumed = false
    isCanBeResolved = true
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
    testImplementation("com.tngtech.archunit:archunit:1.5.1")

    add(aqk16Cpd.name, "net.sourceforge.pmd:pmd-cli:7.29.0")
    add(aqk16Cpd.name, "net.sourceforge.pmd:pmd-kotlin:7.29.0")
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

// AQK-16: Kotlin duplication gate (60 tokens, no normalization of literals or names).
// It is intentionally invoked from the existing quality-detekt CI context.
// PMD CLI keeps lexer/parser errors fatal. Only duplicate-related CLI status is
// suppressed so that we can validate the complete XML and give a safe summary.
tasks.register<JavaExec>("aqk16CpdKotlin") {
    group = "verification"
    description = "Fail on Kotlin production CPD duplications (PMD 7.29.0, 60 tokens)"
    mainClass.set("net.sourceforge.pmd.cli.PmdCli")
    classpath = aqk16Cpd

    val sourceRoot = layout.projectDirectory.dir("src/main/kotlin").asFile
    val report = layout.buildDirectory.file("reports/aqk16-cpd/cpd-60.xml").get().asFile

    args(
        "cpd",
        "--language", "kotlin",
        "--minimum-tokens", "60",
        "--dir", sourceRoot.absolutePath,
        "--encoding", "UTF-8",
        "--format", "xml",
        "--report-file", report.absolutePath,
        "--no-fail-on-violation",
    )

    doFirst {
        if (!report.parentFile.isDirectory && !report.parentFile.mkdirs()) {
            throw GradleException("AQK16_CPD_REPORT_DIRECTORY=FAIL")
        }
        if (report.exists() && !report.delete()) {
            throw GradleException("AQK16_CPD_STALE_REPORT_CLEANUP=FAIL")
        }
    }

    doLast {
        if (!report.isFile || report.length() == 0L) {
            throw GradleException("AQK16_CPD_XML_MISSING_OR_EMPTY")
        }

        // XML may contain source fragments: parse securely and never print them.
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(report)
        val root = document.documentElement
        if (root.localName != "pmd-cpd" || root.getAttribute("pmdVersion") != "7.29.0") {
            throw GradleException("AQK16_CPD_XML_IDENTITY=FAIL")
        }

        val reportedFiles = mutableListOf<java.io.File>()
        var duplications = 0
        var errors = 0
        val entries = root.childNodes
        for (i in 0 until entries.length) {
            val entry = entries.item(i)
            if (entry !is Element) continue
            when (entry.localName) {
                "file" -> {
                    val path = entry.getAttribute("path")
                    if (path.isBlank() || !entry.hasAttribute("totalNumberOfTokens")) {
                        throw GradleException("AQK16_CPD_INVALID_FILE_ENTRY")
                    }
                    reportedFiles.add(file(path).canonicalFile)
                }
                "duplication" -> duplications++
                "error" -> errors++
                else -> throw GradleException("AQK16_CPD_UNKNOWN_XML_ENTRY=${entry.localName}")
            }
        }

        val expectedFiles = fileTree(sourceRoot) {
            include("**/*.kt")
        }.files.map { it.canonicalFile }.toSet()
        val actualFiles = reportedFiles.toSet()
        if (expectedFiles.isEmpty() || reportedFiles.size != expectedFiles.size ||
            actualFiles != expectedFiles) {
            throw GradleException(
                "AQK16_CPD_SCAN_COVERAGE=FAIL expected=${expectedFiles.size} reported=${reportedFiles.size}",
            )
        }

        logger.lifecycle("AQK16_CPD_VERSION=7.29.0")
        logger.lifecycle("AQK16_CPD_MINIMUM_TOKENS=60")
        logger.lifecycle("AQK16_CPD_SCANNED_FILES=${actualFiles.size}")
        logger.lifecycle("AQK16_CPD_XML_ERRORS=$errors")
        logger.lifecycle("AQK16_CPD_DUPLICATION_GROUPS=$duplications")
        logger.lifecycle("AQK16_CPD_SCAN_COVERAGE=PASS")

        if (errors != 0) throw GradleException("AQK16_CPD_ANALYSIS_ERRORS=FAIL")
        if (duplications != 0) throw GradleException("AQK16_CPD_DUPLICATION_GATE=FAIL")
        logger.lifecycle("AQK16_CPD_DUPLICATION_GATE=PASS")
    }
}

// Direct local `clean check` must enforce the same CPD policy as quality-detekt.
tasks.named("check") {
    dependsOn(tasks.named("aqk16CpdKotlin"))
}
