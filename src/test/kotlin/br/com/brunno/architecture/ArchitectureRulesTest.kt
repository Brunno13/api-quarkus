package br.com.brunno.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AQK-16: architectural boundaries of authored production code.
 *
 * Auth may consume users.domain and users.application's validation exception.
 * Shared error mappers may consume feature exceptions, but never HTTP adapters
 * or persistence implementations from those features.
 */
class ArchitectureRulesTest {
    @Test
    fun `ARCH000 required production packages are present`() {
        val packages =
            productionClasses
                .map { it.packageName }
                .toSet()

        for (required in REQUIRED_PACKAGES) {
            assertTrue(required in packages, "Missing production package: $required")
        }
    }

    @Test
    fun `ARCH001 domain must not depend on outer layers`() {
        assertNoDependencies(
            sources = DOMAIN_PACKAGES,
            forbidden =
                arrayOf(
                    "$ROOT.features.auth.api..",
                    "$ROOT.features.auth.application..",
                    "$ROOT.features.auth.cli..",
                    "$ROOT.features.auth.infrastructure..",
                    "$ROOT.features.users.api..",
                    "$ROOT.features.users.application..",
                    "$ROOT.features.users.infrastructure..",
                    "$ROOT.shared..",
                ),
        )
    }

    @Test
    fun `ARCH002 domain must not depend on frameworks or persistence`() {
        assertNoDependencies(
            sources = DOMAIN_PACKAGES,
            forbidden =
                arrayOf(
                    "com.mongodb..",
                    "io.quarkus..",
                    "io.smallrye..",
                    "io.vertx..",
                    "jakarta..",
                    "org.bson..",
                    "org.eclipse.microprofile..",
                ),
        )
    }

    @Test
    fun `ARCH003 application must not depend on adapters`() {
        assertNoDependencies(
            sources =
                arrayOf(
                    "$ROOT.features.auth.application..",
                    "$ROOT.features.users.application..",
                ),
            forbidden =
                arrayOf(
                    "$ROOT.features.auth.api..",
                    "$ROOT.features.auth.cli..",
                    "$ROOT.features.auth.infrastructure..",
                    "$ROOT.features.users.api..",
                    "$ROOT.features.users.infrastructure..",
                    "$ROOT.shared..",
                ),
        )
    }

    @Test
    fun `ARCH004 users must not depend on auth`() {
        assertNoDependencies(
            sources = arrayOf("$ROOT.features.users.."),
            forbidden = arrayOf("$ROOT.features.auth.."),
        )
    }

    @Test
    fun `ARCH005 auth must not depend on users adapters`() {
        assertNoDependencies(
            sources = arrayOf("$ROOT.features.auth.."),
            forbidden =
                arrayOf(
                    "$ROOT.features.users.api..",
                    "$ROOT.features.users.infrastructure..",
                ),
        )
    }

    @Test
    fun `ARCH006 shared must not depend on feature adapters`() {
        assertNoDependencies(
            sources = arrayOf("$ROOT.shared.."),
            forbidden =
                arrayOf(
                    "$ROOT.features.auth.api..",
                    "$ROOT.features.auth.cli..",
                    "$ROOT.features.auth.infrastructure..",
                    "$ROOT.features.users.api..",
                    "$ROOT.features.users.infrastructure..",
                ),
        )
    }

    private fun assertNoDependencies(
        sources: Array<String>,
        forbidden: Array<String>,
    ) {
        noClasses()
            .that()
            .resideInAnyPackage(*sources)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*forbidden)
            .check(productionClasses)
    }

    private companion object {
        const val ROOT = "br.com.brunno"

        val DOMAIN_PACKAGES =
            arrayOf(
                "$ROOT.features.auth.domain..",
                "$ROOT.features.users.domain..",
            )

        val REQUIRED_PACKAGES =
            setOf(
                "$ROOT.features.auth.api",
                "$ROOT.features.auth.application",
                "$ROOT.features.auth.cli",
                "$ROOT.features.auth.domain",
                "$ROOT.features.auth.infrastructure",
                "$ROOT.features.users.api",
                "$ROOT.features.users.application",
                "$ROOT.features.users.domain",
                "$ROOT.features.users.infrastructure",
                "$ROOT.shared.error",
                "$ROOT.shared.health",
            )

        val productionClasses by lazy {
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .withImportOption(ImportOption.DoNotIncludeJars())
                .importPackages(ROOT)
        }
    }
}
