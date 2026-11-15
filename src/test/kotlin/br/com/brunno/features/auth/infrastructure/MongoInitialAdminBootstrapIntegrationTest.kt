package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.BootstrapFirstAdminCommand
import br.com.brunno.features.auth.application.BootstrapFirstAdminUseCase
import br.com.brunno.features.auth.application.InitialAdminAlreadyConfiguredException
import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import br.com.brunno.features.users.domain.UserRole
import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Explicit MongoDB integration suite; excluded from ordinary `test` and `check`.
 * Every test creates its own randomly named database and drops only that database
 * after checking its ownership marker. Never point this suite at production MongoDB.
 *
 * Mongo-backed test users deliberately avoid a Quarkus CDI context, so these
 * tests exercise the real Mongo bootstrap reservation adapter and store directly.
 * The ordinary MongoUserRepository is covered by its separate integration tests.
 */
@Tag("mongodb-integration")
class MongoInitialAdminBootstrapIntegrationTest {

    @Test
    fun `first bootstrap persists hashed credential then admin and refuses a second`() =
        withIsolatedDatabase { client, db, dbName ->
            val gate = MongoInitialAdminBootstrapWriteGate(client, dbName)
            val hasher = BcryptPasswordHasher()
            val store = ReservedInitialAdminBootstrapStore(gate, MongoBackedUsers(db))
            val password = "isolated-test-password"
            val bootstrap = BootstrapFirstAdminUseCase(store, hasher)

            val admin = bootstrap.execute(
                BootstrapFirstAdminCommand(
                    "  First Admin  ", "  ADMIN@Example.Test  ", password
                )
            )

            assertEquals("First Admin", admin.name)
            assertEquals("admin@example.test", admin.email)
            assertEquals(setOf(UserRole.ADMIN), admin.roles)
            assertEquals(1L, db.getCollection("admin_bootstrap").countDocuments())
            assertEquals(1L, db.getCollection("users").countDocuments())
            assertEquals(1L, db.getCollection("user_credentials").countDocuments())

            val credential = db.getCollection("user_credentials")
                .find(Filters.eq("_id", admin.id.toString())).first()
            assertNotNull(credential)
            val hash = requireNotNull(credential).getString("passwordHash")
            assertFalse(hash.contains(password))
            assertTrue(hasher.verify(password, hash))

            assertThrows(InitialAdminAlreadyConfiguredException::class.java) {
                bootstrap.execute(
                    BootstrapFirstAdminCommand("Another Admin", "other@example.test", password)
                )
            }
            assertEquals(1L, db.getCollection("users").countDocuments())
            assertEquals(1L, db.getCollection("user_credentials").countDocuments())
        }

    @Test
    fun `duplicate reservation never removes or replaces initial claim`() =
        withIsolatedDatabase { client, db, dbName ->
            val gate = MongoInitialAdminBootstrapWriteGate(client, dbName)
            gate.reserveOnce()
            assertThrows(InitialAdminAlreadyConfiguredException::class.java) {
                gate.reserveOnce()
            }
            val reservation = db.getCollection("admin_bootstrap")
                .find(Filters.eq("_id", "initial-admin")).first()
            assertEquals("consumed", requireNotNull(reservation).getString("state"))
            assertEquals(1L, db.getCollection("admin_bootstrap").countDocuments())
        }

    @Test
    fun `concurrent reservations have exactly one winner`() =
        withIsolatedDatabase { client, db, dbName ->
            val gate = MongoInitialAdminBootstrapWriteGate(client, dbName)
            val pool = Executors.newFixedThreadPool(6)
            val start = CountDownLatch(1)
            try {
                val attempts = (1..6).map {
                    pool.submit(Callable {
                        check(start.await(10, TimeUnit.SECONDS))
                        try {
                            gate.reserveOnce()
                            true
                        } catch (_: InitialAdminAlreadyConfiguredException) {
                            false
                        }
                    })
                }
                start.countDown()
                val results = attempts.map { it.get(30, TimeUnit.SECONDS) }
                assertEquals(1, results.count { it })
                assertEquals(1L, db.getCollection("admin_bootstrap").countDocuments())
            } finally {
                pool.shutdownNow()
            }
        }

    @Test
    fun `preexisting admin consumes reservation without creating new credentials`() =
        withIsolatedDatabase { client, db, dbName ->
            db.getCollection("users").insertOne(
                Document("_id", UUID.randomUUID().toString())
                    .append("name", "Already Admin")
                    .append("email", "existing@example.test")
                    .append("roles", listOf("ADMIN"))
            )
            val gate = MongoInitialAdminBootstrapWriteGate(client, dbName)
            val store = ReservedInitialAdminBootstrapStore(gate, MongoBackedUsers(db))
            val (user, credential) = candidate()
            assertThrows(InitialAdminAlreadyConfiguredException::class.java) {
                store.createFirstAdmin(user, credential)
            }
            assertEquals(1L, db.getCollection("admin_bootstrap").countDocuments())
            assertEquals(1L, db.getCollection("users").countDocuments())
            assertEquals(0L, db.getCollection("user_credentials").countDocuments())
        }

    @Test
    fun `duplicate credential fails closed before making admin visible`() =
        withIsolatedDatabase { client, db, dbName ->
            val (user, credential) = candidate()
            db.getCollection("user_credentials").insertOne(
                Document("_id", user.id.toString()).append("passwordHash", "existing-test-hash")
            )
            val gate = MongoInitialAdminBootstrapWriteGate(client, dbName)
            val store = ReservedInitialAdminBootstrapStore(gate, MongoBackedUsers(db))

            val error = assertThrows(MongoException::class.java) {
                store.createFirstAdmin(user, credential)
            }
            assertEquals(11000, error.code)
            assertEquals(1L, db.getCollection("admin_bootstrap").countDocuments())
            assertEquals(0L, db.getCollection("users").countDocuments())
            assertEquals(1L, db.getCollection("user_credentials").countDocuments())
            assertThrows(InitialAdminAlreadyConfiguredException::class.java) {
                store.createFirstAdmin(user, credential)
            }
        }

    @Test
    fun `failed user write leaves durable credential and consumed reservation`() =
        withIsolatedDatabase { client, db, dbName ->
            val gate = MongoInitialAdminBootstrapWriteGate(client, dbName)
            val store = ReservedInitialAdminBootstrapStore(
                gate, MongoBackedUsers(db, failOnCreate = true)
            )
            val (user, credential) = candidate()
            assertThrows(IllegalStateException::class.java) {
                store.createFirstAdmin(user, credential)
            }
            assertEquals(1L, db.getCollection("admin_bootstrap").countDocuments())
            assertEquals(1L, db.getCollection("user_credentials").countDocuments())
            assertEquals(0L, db.getCollection("users").countDocuments())
            assertThrows(InitialAdminAlreadyConfiguredException::class.java) {
                store.createFirstAdmin(user, credential)
            }
        }

    private fun candidate(): Pair<User, UserCredential> {
        val id = UUID.randomUUID()
        return User(id, "Initial Admin", "initial@example.test", setOf(UserRole.ADMIN)) to
            UserCredential(id, "test-only-encoded-hash")
    }

    private fun withIsolatedDatabase(block: (MongoClient, MongoDatabase, String) -> Unit) {
        val uri = System.getenv("MONGODB_URI")?.takeIf(String::isNotBlank)
            ?: error("MONGODB_URI is required for MongoDB integration tests")
        val baseName = System.getenv("MONGODB_DATABASE")?.takeIf(String::isNotBlank)
            ?: error("MONGODB_DATABASE is required for MongoDB integration tests")
        require(baseName.startsWith("api_quarkus_it_")) {
            "Only an api_quarkus_it_ prefixed database is allowed"
        }

        val nonce = UUID.randomUUID().toString().replace("-", "")
        val name = "api_quarkus_it_bootstrap_$nonce"
        val markerCollection = "bootstrap_integration_owner"
        MongoClients.create(uri).use { client ->
            val db = client.getDatabase(name)
            require(db.listCollectionNames().first() == null) {
                "Refusing to use an existing bootstrap integration database"
            }
            db.getCollection(markerCollection).insertOne(
                Document("_id", "owner").append("nonce", nonce)
            )
            try {
                block(client, db, name)
            } finally {
                val marker = db.getCollection(markerCollection)
                    .find(Filters.eq("_id", "owner")).first()
                check(marker?.getString("nonce") == nonce) {
                    "Refusing to drop a database without its original ownership marker"
                }
                db.drop()
            }
        }
    }

    private class MongoBackedUsers(
        private val database: MongoDatabase,
        private val failOnCreate: Boolean = false
    ) : UserRepository {
        override fun findById(id: UUID): User? =
            database.getCollection("users").find(Filters.eq("_id", id.toString()))
                .first()?.let(::toUser)

        override fun findByEmail(email: String): User? =
            database.getCollection("users").find(Filters.eq("email", email))
                .first()?.let(::toUser)

        override fun create(user: User): User {
            if (failOnCreate) throw IllegalStateException("Simulated user repository failure")
            database.getCollection("users").insertOne(
                Document("_id", user.id.toString())
                    .append("name", user.name)
                    .append("email", user.email)
                    .append("roles", user.roles.map { it.name })
            )
            return user
        }

        private fun toUser(document: Document): User = User(
            UUID.fromString(document.getString("_id")),
            document.getString("name"),
            document.getString("email"),
            document.getList("roles", String::class.java).map(UserRole::valueOf).toSet()
        )
    }
}
