
package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.PasswordHasher
import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.auth.domain.UserCredentialAlreadyExistsException
import br.com.brunno.features.auth.domain.UserCredentialRepository
import br.com.brunno.support.MongoIntegrationTestProfile
import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
@TestProfile(MongoIntegrationTestProfile::class)
@Tag("mongodb-integration")
class MongoUserCredentialRepositoryIntegrationTest {

    @Inject
    lateinit var repository: UserCredentialRepository

    @Inject
    lateinit var passwordHasher: PasswordHasher

    @Inject
    lateinit var mongoClient: MongoClient

    @ConfigProperty(name = "quarkus.mongodb.database")
    lateinit var databaseName: String

    @Test
    fun `creates and retrieves credentials by user UUID`() {
        val userId = UUID.randomUUID()
        val hash = passwordHasher.hash("Integration-Password-2026!")

        val collection = mongoClient
            .getDatabase(databaseName)
            .getCollection("user_credentials")

        try {
            val created = repository.create(
                UserCredential(userId, hash)
            )

            assertEquals(userId, created.userId)
            assertEquals(hash, created.passwordHash)

            val retrieved = repository.findByUserId(userId)

            assertNotNull(retrieved)
            assertEquals(userId, retrieved?.userId)
            assertEquals(hash, retrieved?.passwordHash)

            val document = collection.find(
                Filters.eq("_id", userId.toString())
            ).first()

            assertNotNull(document)
            assertEquals(
                userId.toString(),
                document?.getString("_id")
            )
        } finally {
            collection.deleteOne(
                Filters.eq("_id", userId.toString())
            )
        }
    }

    @Test
    fun `returns null for unknown user credentials`() {
        val unknownUserId = UUID.randomUUID()

        assertNull(repository.findByUserId(unknownUserId))
    }

    @Test
    fun `persists bcrypt hash without plaintext password`() {
        val userId = UUID.randomUUID()
        val password = "Integration-Secure-Password-2026!"
        val hash = passwordHasher.hash(password)

        val collection = mongoClient
            .getDatabase(databaseName)
            .getCollection("user_credentials")

        try {
            repository.create(UserCredential(userId, hash))

            val document = collection.find(
                Filters.eq("_id", userId.toString())
            ).first()

            assertNotNull(document)

            val storedHash = document?.getString("passwordHash")

            assertEquals(hash, storedHash)
            assertFalse(storedHash == password)
            assertNull(document?.get("password"))

            assertTrue(
                passwordHasher.verify(password, requireNotNull(storedHash))
            )
            assertFalse(
                passwordHasher.verify(
                    "Incorrect-Password",
                    storedHash
                )
            )
        } finally {
            collection.deleteOne(
                Filters.eq("_id", userId.toString())
            )
        }
    }

    @Test
    fun `rejects duplicate credentials without overwriting original`() {
        val userId = UUID.randomUUID()
        val originalHash = passwordHasher.hash(
            "Original-Password-2026!"
        )
        val duplicateHash = passwordHasher.hash(
            "Different-Password-2026!"
        )

        val collection = mongoClient
            .getDatabase(databaseName)
            .getCollection("user_credentials")

        try {
            repository.create(
                UserCredential(userId, originalHash)
            )

            val error = assertThrows(
                UserCredentialAlreadyExistsException::class.java
            ) {
                repository.create(
                    UserCredential(userId, duplicateHash)
                )
            }

            val cause = error.cause

            assertTrue(cause is MongoException)
            assertEquals(11000, (cause as MongoException).code)

            val persisted = repository.findByUserId(userId)

            assertNotNull(persisted)
            assertEquals(originalHash, persisted?.passwordHash)

            assertEquals(
                1L,
                collection.countDocuments(
                    Filters.eq("_id", userId.toString())
                )
            )
        } finally {
            collection.deleteOne(
                Filters.eq("_id", userId.toString())
            )
        }
    }
}
