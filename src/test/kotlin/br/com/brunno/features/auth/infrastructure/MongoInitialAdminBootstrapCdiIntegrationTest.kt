package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.AuthenticatePersistedUserUseCase
import br.com.brunno.features.auth.application.BootstrapFirstAdminCommand
import br.com.brunno.features.auth.application.BootstrapFirstAdminUseCase
import br.com.brunno.features.auth.application.InitialAdminAlreadyConfiguredException
import br.com.brunno.features.auth.application.InitialAdminBootstrapStore
import br.com.brunno.features.auth.application.InvalidCredentialsException
import br.com.brunno.features.auth.application.PasswordHasher
import br.com.brunno.features.users.domain.UserRole
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * A separate CDI-enabled MongoDB integration check. The profile creates a
 * unique database, rather than using the database named in MONGODB_DATABASE.
 * This test is excluded from the default database-free quality gate.
 */
@QuarkusTest
@TestProfile(BootstrapMongoCdiIntegrationProfile::class)
@Tag("mongodb-integration")
class MongoInitialAdminBootstrapCdiIntegrationTest {

    @Inject
    lateinit var mongoClient: MongoClient

    @Inject
    lateinit var store: InitialAdminBootstrapStore

    @Inject
    @field:ConfigProperty(name = "quarkus.mongodb.database")
    lateinit var configuredDatabaseName: String

    @Inject
    lateinit var hasher: PasswordHasher

    @Inject
    lateinit var authenticate: AuthenticatePersistedUserUseCase

    @Test
    fun `bootstraps and authenticates ADMIN using real CDI Mongo repositories`() {
        // Read the active Quarkus configuration, not a static profile property.
        // Quarkus test augmentation may load the profile in another classloader.
        val dbName = configuredDatabaseName
        check(Regex("^api_quarkus_it_bootstrap_cdi_[0-9a-f]{32}$").matches(dbName)) {
            "Refusing to use an unexpected MongoDB integration database"
        }
        val nonce = dbName.removePrefix("api_quarkus_it_bootstrap_cdi_")
        val database = mongoClient.getDatabase(dbName)
        val ownershipCollection = database.getCollection("bootstrap_cdi_integration_owner")

        check(database.listCollectionNames().first() == null) {
            "Refusing to use an existing MongoDB integration database"
        }

        ownershipCollection.insertOne(Document("_id", "owner").append("nonce", nonce))
        try {
            val password = "bootstrap-cdi-test-password"
            val bootstrap = BootstrapFirstAdminUseCase(store, hasher)
            val created = bootstrap.execute(
                BootstrapFirstAdminCommand(
                    name = "First Administrator",
                    email = "  BOOTSTRAP@Example.Test  ",
                    rawPassword = password
                )
            )

            assertEquals(setOf(UserRole.ADMIN), created.roles)
            assertEquals("bootstrap@example.test", created.email)
            assertEquals(1L, database.getCollection("admin_bootstrap").countDocuments())
            assertEquals(1L, database.getCollection("users").countDocuments())
            assertEquals(1L, database.getCollection("user_credentials").countDocuments())

            val persisted = database.getCollection("user_credentials")
                .find(Filters.eq("_id", created.id.toString())).first()
            assertNotNull(persisted)
            val hashed = requireNotNull(persisted).getString("passwordHash")
            check(!hashed.contains(password)) { "Password must never be persisted in cleartext" }
            assertEquals(true, hasher.verify(password, hashed))

            val signedIn = authenticate.execute(" BOOTSTRAP@example.test ", password)
            assertEquals(created.id, signedIn.id)
            assertEquals(setOf(UserRole.ADMIN), signedIn.roles)

            assertThrows(InvalidCredentialsException::class.java) {
                authenticate.execute(created.email, "wrong-password")
            }
            assertThrows(InitialAdminAlreadyConfiguredException::class.java) {
                bootstrap.execute(
                    BootstrapFirstAdminCommand(
                        "Second Administrator", "second@example.test", password
                    )
                )
            }
            assertEquals(1L, database.getCollection("users").countDocuments())
            assertEquals(1L, database.getCollection("user_credentials").countDocuments())
        } finally {
            val marker = ownershipCollection.find(Filters.eq("_id", "owner")).first()
            check(marker?.getString("nonce") == nonce) {
                "Refusing to drop a database without its original ownership marker"
            }
            database.drop()
        }
    }
}

/** Must remain separate from the pre-existing MongoIntegrationTestProfile. */
class BootstrapMongoCdiIntegrationProfile : QuarkusTestProfile {
    companion object {
        val nonce: String = UUID.randomUUID().toString().replace("-", "")
        val databaseName: String = "api_quarkus_it_bootstrap_cdi_$nonce"
    }

    override fun getConfigOverrides(): Map<String, String> {
        val uri = System.getenv("MONGODB_URI")?.takeIf(String::isNotBlank)
            ?: error("MONGODB_URI is required for MongoDB integration tests")
        val guard = System.getenv("MONGODB_DATABASE")?.takeIf(String::isNotBlank)
            ?: error("MONGODB_DATABASE is required for MongoDB integration tests")
        require(guard.startsWith("api_quarkus_it_")) {
            "MongoDB integration requires a dedicated api_quarkus_it_ database prefix"
        }

        return mapOf(
            "app.persistence.mongodb.enabled" to "true",
            "quarkus.mongodb.active" to "true",
            "quarkus.mongodb.devservices.enabled" to "false",
            "quarkus.mongodb.health.enabled" to "false",
            "quarkus.mongodb.connection-string" to uri,
            "quarkus.mongodb.database" to databaseName
        )
    }
}
