package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.LoginAttemptDecision
import br.com.brunno.features.auth.application.LoginRateLimitPolicy
import br.com.brunno.features.auth.application.LoginRateLimitQuota
import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Real-Mongo tests, excluded from normal clean check. Uses disposable owned
 * databases only; no production database or service should be used here.
 */
@Tag("mongodb-integration")
class MongoLoginAttemptRateLimiterIntegrationTest {
    private val secret = ByteArray(32) { (it + 1).toByte() }
    // TTL must not delete documents while this integration suite is executing.
    private val now = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MINUTES)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `identity quota allows exact budget and rejects excess attempts`() =
        withIsolatedDatabase { client, db, name ->
            val limiter = limiter(client, name, identityMax = 2, originMax = 10)
            assertEquals(LoginAttemptDecision.Allowed,
                limiter.reserveAttempt("person@example.test", "192.0.2.5"))
            assertEquals(LoginAttemptDecision.Allowed,
                limiter.reserveAttempt("person@example.test", "192.0.2.5"))
            assertEquals(LoginAttemptDecision.Rejected(60),
                limiter.reserveAttempt("person@example.test", "192.0.2.5"))

            val docs = db.getCollection(MongoLoginAttemptRateLimiter.COLLECTION_NAME)
                .find().toList()
            assertEquals(2, docs.size)
            assertEquals(listOf(3L, 3L), docs.map { (it["attempts"] as Number).toLong() })
            assertTrue(docs.any { it.getString("_id").startsWith("v1:identity:") })
            assertTrue(docs.any { it.getString("_id").startsWith("v1:origin:") })
        }

    @Test
    fun `origin quota aggregates attempts across different identities`() =
        withIsolatedDatabase { client, db, name ->
            val limiter = limiter(client, name, identityMax = 10, originMax = 2)
            assertEquals(LoginAttemptDecision.Allowed,
                limiter.reserveAttempt("first@example.test", "192.0.2.10"))
            assertEquals(LoginAttemptDecision.Allowed,
                limiter.reserveAttempt("second@example.test", "192.0.2.10"))
            assertEquals(LoginAttemptDecision.Rejected(60),
                limiter.reserveAttempt("third@example.test", "192.0.2.10"))
            val docs = db.getCollection(MongoLoginAttemptRateLimiter.COLLECTION_NAME)
                .find().toList()
            assertEquals(3, docs.size) // origin and the first two identities
            assertEquals(1, docs.count { (it["attempts"] as Number).toLong() == 3L })
        }

    @Test
    fun `new fixed window gets independent counters and exact expiration dates`() =
        withIsolatedDatabase { client, db, name ->
            val first = limiter(client, name, identityMax = 1, originMax = 1)
            assertEquals(LoginAttemptDecision.Allowed,
                first.reserveAttempt("person@example.test", "192.0.2.1"))
            assertEquals(LoginAttemptDecision.Rejected(60),
                first.reserveAttempt("person@example.test", "192.0.2.1"))

            val nextClock = Clock.fixed(now.plusSeconds(61), ZoneOffset.UTC)
            val next = limiter(client, name, identityMax = 1, originMax = 1, clock = nextClock)
            assertEquals(LoginAttemptDecision.Allowed,
                next.reserveAttempt("person@example.test", "192.0.2.1"))
            assertEquals(LoginAttemptDecision.Rejected(59),
                next.reserveAttempt("person@example.test", "192.0.2.1"))

            val docs = db.getCollection(MongoLoginAttemptRateLimiter.COLLECTION_NAME)
                .find().toList()
            assertEquals(4, docs.size)
            assertEquals(2, docs.count { it.getDate("expireAt") == Date.from(now.plusSeconds(60)) })
            assertEquals(2, docs.count { it.getDate("expireAt") == Date.from(now.plusSeconds(120)) })
        }

    @Test
    fun `independent limiter instances share the same identity quota`() =
        withIsolatedDatabase { client, _, name ->
            val first = limiter(client, name, identityMax = 2, originMax = 10)
            val second = limiter(client, name, identityMax = 2, originMax = 10)
            assertEquals(LoginAttemptDecision.Allowed,
                first.reserveAttempt("person@example.test", "192.0.2.1"))
            assertEquals(LoginAttemptDecision.Allowed,
                second.reserveAttempt("person@example.test", "192.0.2.2"))
            assertEquals(LoginAttemptDecision.Rejected(60),
                first.reserveAttempt("person@example.test", "192.0.2.3"))
        }

    @Test
    fun `concurrent requests permit at most the configured identity quota`() =
        withIsolatedDatabase { client, db, name ->
            val limiter = limiter(client, name, identityMax = 3, originMax = 25)
            val executor = Executors.newFixedThreadPool(12)
            val start = CountDownLatch(1)
            try {
                val results = (1..12).map {
                    executor.submit(Callable {
                        check(start.await(10, TimeUnit.SECONDS))
                        limiter.reserveAttempt("person@example.test", "192.0.2.9")
                    })
                }
                start.countDown()
                val decisions = results.map { it.get(40, TimeUnit.SECONDS) }
                assertEquals(3, decisions.count { it == LoginAttemptDecision.Allowed })
                assertEquals(9, decisions.count { it is LoginAttemptDecision.Rejected })
                val docs = db.getCollection(MongoLoginAttemptRateLimiter.COLLECTION_NAME)
                    .find().toList()
                assertEquals(2, docs.size)
                assertTrue(docs.all { (it["attempts"] as Number).toLong() == 12L })
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `stored document contains only pseudonymous identifiers and TTL index`() =
        withIsolatedDatabase { client, db, name ->
            val limiter = limiter(client, name, identityMax = 3, originMax = 10)
            assertEquals(LoginAttemptDecision.Allowed,
                limiter.reserveAttempt("private@example.test", "192.0.2.27"))
            val collection = db.getCollection(MongoLoginAttemptRateLimiter.COLLECTION_NAME)
            val json = collection.find().toList().joinToString { it.toJson() }
            assertFalse(json.contains("private@example.test"))
            assertFalse(json.contains("192.0.2.27"))
            assertEquals(2L, collection.countDocuments())
            val ttlIndex = collection.listIndexes().toList().find {
                (it["key"] as? Document)?.getInteger("expireAt") == 1
            }
            assertEquals(0L, (requireNotNull(ttlIndex)["expireAfterSeconds"] as Number).toLong())
        }

    @Test
    fun `invalid identifiers cannot create counters`() =
        withIsolatedDatabase { client, db, name ->
            val limiter = limiter(client, name, identityMax = 3, originMax = 10)
            assertThrows(IllegalArgumentException::class.java) {
                limiter.reserveAttempt(" PERSON@example.test ", "192.0.2.1")
            }
            assertThrows(IllegalArgumentException::class.java) {
                limiter.reserveAttempt("person@example.test", "  ")
            }
            assertEquals(0L,
                db.getCollection(MongoLoginAttemptRateLimiter.COLLECTION_NAME).countDocuments())
        }

    @Test
    fun `corrupted Mongo counter fails closed rather than allowing a login`() =
        withIsolatedDatabase { client, db, name ->
            val limiter = limiter(client, name, identityMax = 3, originMax = 10)
            assertEquals(LoginAttemptDecision.Allowed,
                limiter.reserveAttempt("person@example.test", "192.0.2.1"))
            val collection = db.getCollection(MongoLoginAttemptRateLimiter.COLLECTION_NAME)
            val identityDoc = collection.find().toList().first {
                it.getString("_id").startsWith("v1:identity:")
            }
            collection.updateOne(
                Filters.eq("_id", identityDoc.getString("_id")),
                Updates.set("attempts", "corrupted")
            )
            assertThrows(MongoException::class.java) {
                limiter.reserveAttempt("person@example.test", "192.0.2.1")
            }
        }

    private fun limiter(
        client: MongoClient,
        name: String,
        identityMax: Int,
        originMax: Int,
        clock: Clock = this.clock
    ): MongoLoginAttemptRateLimiter = MongoLoginAttemptRateLimiter(
        client, name,
        LoginRateLimitPolicy(
            LoginRateLimitQuota(identityMax, 60),
            LoginRateLimitQuota(originMax, 60)
        ),
        secret, clock
    )

    private fun withIsolatedDatabase(block: (MongoClient, MongoDatabase, String) -> Unit) {
        val uri = System.getenv("MONGODB_URI")?.takeIf(String::isNotBlank)
            ?: error("MONGODB_URI is required for MongoDB integration tests")
        val safetyName = System.getenv("MONGODB_DATABASE")?.takeIf(String::isNotBlank)
            ?: error("MONGODB_DATABASE is required for MongoDB integration tests")
        require(safetyName.startsWith("api_quarkus_it_")) {
            "Only a dedicated api_quarkus_it_ MongoDB integration environment is allowed"
        }

        val nonce = UUID.randomUUID().toString().replace("-", "")
        val name = "api_quarkus_it_rate_limit_$nonce"
        val ownershipCollection = "rate_limit_integration_owner"
        MongoClients.create(uri).use { client ->
            val db = client.getDatabase(name)
            check(db.listCollectionNames().first() == null) {
                "Refusing to use a preexisting integration database"
            }
            db.getCollection(ownershipCollection).insertOne(
                Document("_id", "owner").append("nonce", nonce)
            )
            try {
                block(client, db, name)
            } finally {
                val owner = db.getCollection(ownershipCollection)
                    .find(Filters.eq("_id", "owner")).first()
                check(owner?.getString("nonce") == nonce) {
                    "Refusing to drop a database without its exact ownership marker"
                }
                db.drop()
            }
        }
    }
}
