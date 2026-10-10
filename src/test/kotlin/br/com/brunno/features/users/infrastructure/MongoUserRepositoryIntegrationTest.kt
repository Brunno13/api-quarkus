package br.com.brunno.features.users.infrastructure

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import br.com.brunno.features.users.domain.UserRole
import br.com.brunno.support.MongoIntegrationTestProfile
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
}
