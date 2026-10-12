package br.com.brunno.features.users.infrastructure

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import br.com.brunno.features.users.domain.UserRole
import br.com.brunno.support.MongoIntegrationTestProfile
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.MongoException
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
@TestProfile(MongoIntegrationTestProfile::class)
@Tag("mongodb-integration")
class MongoUserRepositoryIntegrationTest {

    @Inject
    lateinit var repository: UserRepository

    @Inject
    lateinit var mongoClient: MongoClient

    @ConfigProperty(name = "quarkus.mongodb.database")
    lateinit var databaseName: String

    @Test
    fun `creates and retrieves user from MongoDB`() {
        val id = UUID.randomUUID()

        val user = User(
            id = id,
            name = "Integration Admin",
            email = "  ADMIN@Example.COM  ",
            roles = setOf(UserRole.USER, UserRole.ADMIN)
        )

        val collection = mongoClient
            .getDatabase(databaseName)
            .getCollection("users")

        try {
            // Create
            val created = repository.create(user)

            assertEquals(id, created.id)
            assertEquals("admin@example.com", created.email)
            assertEquals(user.roles, created.roles)

            // Find by UUID
            val byId = repository.findById(id)

            assertNotNull(byId)
            assertEquals(id, byId?.id)
            assertEquals(created, byId)

            // Find by normalized email
            val byEmail = repository.findByEmail(
                "  ADMIN@EXAMPLE.COM  "
            )

            assertNotNull(byEmail)
            assertEquals(id, byEmail?.id)
            assertEquals(created, byEmail)

            // Inspect BSON representation
            val document = collection
                .find(Filters.eq("_id", id.toString()))
                .first()

            assertNotNull(document)
            assertEquals(
                id.toString(),
                document?.getString("_id")
            )
            assertEquals(
                "admin@example.com",
                document?.getString("email")
            )
        } finally {
            // Remove only the document created by this test.
            collection.deleteOne(
                Filters.eq("_id", id.toString())
            )
        }
    }

    @Test
    fun `returns null when user id does not exist`() {
        val unknownId = UUID.randomUUID()

        val result = repository.findById(unknownId)

        assertNull(result)
    }

    @Test
    fun `returns null when user email does not exist`() {
        val unknownEmail = "  MISSING-${UUID.randomUUID()}@EXAMPLE.COM  "

        val result = repository.findByEmail(unknownEmail)

        assertNull(result)
    }

    @Test
    fun `rejects duplicate user emails`() {
        val originalId = UUID.randomUUID()
        val duplicateId = UUID.randomUUID()
        val email = "duplicate-${UUID.randomUUID()}@example.com"

        val original = User(
            id = originalId,
            name = "Original User",
            email = email,
            roles = setOf(UserRole.USER)
        )

        val duplicate = User(
            id = duplicateId,
            name = "Duplicate User",
            email = email,
            roles = setOf(UserRole.USER)
        )

        val collection = mongoClient
            .getDatabase(databaseName)
            .getCollection("users")

        try {
            repository.create(original)

            val error = assertThrows(MongoException::class.java) {
                repository.create(duplicate)
            }

            // MongoDB duplicate key error (E11000).
            assertEquals(11000, error.code)

            // The original user must remain unchanged.
            assertEquals(
                originalId,
                repository.findByEmail(email)?.id
            )

            // The duplicate must not have been persisted.
            assertNull(repository.findById(duplicateId))
        } finally {
            // Clean up only documents belonging to this test.
            collection.deleteOne(
                Filters.eq("_id", originalId.toString())
            )

            collection.deleteOne(
                Filters.eq("_id", duplicateId.toString())
            )
        }
    }

    @Test
    fun `rejects duplicate emails after normalization`() {
        val originalId = UUID.randomUUID()
        val duplicateId = UUID.randomUUID()

        val suffix = UUID.randomUUID().toString()
        val normalizedEmail = "admin-$suffix@example.com"

        val original = User(
            id = originalId,
            name = "Original Admin",
            email = "  ADMIN-$suffix@EXAMPLE.COM  ",
            roles = setOf(UserRole.ADMIN)
        )

        val duplicate = User(
            id = duplicateId,
            name = "Duplicate Admin",
            email = normalizedEmail,
            roles = setOf(UserRole.ADMIN)
        )

        val collection = mongoClient
            .getDatabase(databaseName)
            .getCollection("users")

        try {
            val created = repository.create(original)

            assertEquals(normalizedEmail, created.email)

            val error = assertThrows(MongoException::class.java) {
                repository.create(duplicate)
            }

            assertEquals(11000, error.code)

            // Only the original user must exist.
            assertEquals(
                originalId,
                repository.findByEmail(normalizedEmail)?.id
            )

            assertNull(repository.findById(duplicateId))

            // Verify the normalized value stored in MongoDB.
            val document = collection
                .find(Filters.eq("_id", originalId.toString()))
                .first()

            assertNotNull(document)
            assertEquals(
                normalizedEmail,
                document?.getString("email")
            )
        } finally {
            collection.deleteOne(
                Filters.eq("_id", originalId.toString())
            )

            collection.deleteOne(
                Filters.eq("_id", duplicateId.toString())
            )
        }
    }
}
