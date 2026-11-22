package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.LoginAttemptDecision
import br.com.brunno.features.auth.application.LoginAttemptRateLimiter
import br.com.brunno.features.auth.application.LoginRateLimitPolicy
import br.com.brunno.features.auth.application.LoginRateLimitQuota
import br.com.brunno.features.users.domain.UserEmail
import com.mongodb.MongoException
import com.mongodb.WriteConcern
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import org.bson.Document
import java.time.Clock
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * MongoDB-backed fixed-window admission accounting (infrastructure only).
 * Created by the production CDI producer; not itself a CDI bean.
 *
 * Every admitted login consumes the origin quota first, then the identity
 * quota. If the identity quota rejects, the origin debit is intentionally not
 * rolled back; transactions are not required on a standalone MongoDB host.
 * Any MongoDB error propagates and prevents authentication at the HTTP boundary.
 *
 * A single atomic findOneAndUpdate on _id provides per-bucket concurrency
 * safety. Both successful and failed logins count: admission occurs before
 * BCrypt work and before JWT issuance.
 */
class MongoLoginAttemptRateLimiter(
    mongoClient: MongoClient,
    databaseName: String,
    private val policy: LoginRateLimitPolicy,
    secret: ByteArray,
    private val clock: Clock = Clock.systemUTC(),
) : LoginAttemptRateLimiter {
    companion object {
        const val COLLECTION_NAME: String = "login_rate_limit_windows"
    }

    private val keys = LoginRateLimitKeyHasher(secret)
    private val collection: MongoCollection<Document>
    private val indexLock = Any()

    @Volatile private var ttlIndexReady = false

    init {
        require(databaseName.isNotBlank()) { "MongoDB database name is required" }
        collection =
            mongoClient
                .getDatabase(databaseName)
                .getCollection(COLLECTION_NAME)
                .withWriteConcern(WriteConcern.JOURNALED)
    }

    override fun reserveAttempt(
        normalizedEmail: String,
        clientIp: String,
    ): LoginAttemptDecision {
        require(
            UserEmail.isValid(normalizedEmail) &&
                normalizedEmail == UserEmail.normalize(normalizedEmail),
        ) {
            "A valid normalized email is required"
        }
        require(clientIp.isNotBlank() && clientIp.length <= 64) {
            "A verified client IP address is required"
        }

        ensureTtlIndex()
        val now = clock.instant().epochSecond

        // Origin first blocks password-spraying before identity counters are touched.
        val origin = incrementAndDecide("origin", clientIp, policy.perOrigin, now)
        if (origin is LoginAttemptDecision.Rejected) return origin

        return incrementAndDecide("identity", normalizedEmail, policy.perIdentity, now)
    }

    private fun incrementAndDecide(
        dimension: String,
        identifier: String,
        quota: LoginRateLimitQuota,
        nowSeconds: Long,
    ): LoginAttemptDecision {
        val bucket = Math.floorDiv(nowSeconds, quota.windowSeconds)
        val secondsRemaining = quota.windowSeconds - Math.floorMod(nowSeconds, quota.windowSeconds)
        val endSeconds = Math.multiplyExact(Math.addExact(bucket, 1L), quota.windowSeconds)
        val id = keys.keyFor(dimension, identifier, bucket)

        val filter = Filters.eq("_id", id)
        val update =
            Updates.combine(
                Updates.inc("attempts", 1L),
                Updates.setOnInsert("expireAt", Date.from(Instant.ofEpochSecond(endSeconds))),
            )
        val options =
            FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(ReturnDocument.AFTER)

        // Concurrent upserts may encounter a duplicate-key race on first use.
        // Retry *only* the duplicate-key case; never retry unknown network/write
        // outcomes, which might already have consumed a quota.
        val document =
            try {
                collection.findOneAndUpdate(filter, update, options)
            } catch (error: MongoException) {
                if (error.code != 11000) throw error
                collection.findOneAndUpdate(filter, update, options)
            } ?: error("MongoDB rate-limit counter update returned no document")

        val count =
            (document["attempts"] as? Number)?.toLong()
                ?: error("MongoDB rate-limit counter is missing or invalid")
        check(count > 0) { "MongoDB rate-limit counter must be positive" }

        return if (count <= quota.maxAttempts) {
            LoginAttemptDecision.Allowed
        } else {
            LoginAttemptDecision.Rejected(secondsRemaining)
        }
    }

    private fun ensureTtlIndex() {
        if (ttlIndexReady) return
        synchronized(indexLock) {
            if (ttlIndexReady) return
            collection.createIndex(
                Indexes.ascending("expireAt"),
                IndexOptions().expireAfter(0L, TimeUnit.SECONDS),
            )
            ttlIndexReady = true
        }
    }
}
