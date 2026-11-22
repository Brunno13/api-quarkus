package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.InitialAdminAlreadyConfiguredException
import br.com.brunno.features.auth.domain.UserCredential
import com.mongodb.MongoException
import com.mongodb.WriteConcern
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Provider
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.Date

/**
 * Uses MongoDB's unique _id for a permanent, one-time reservation.
 * Both the reservation and the credential require journal acknowledgement.
 * No TTL, rollback, or automatic retry of a consumed reservation.
 */
@ApplicationScoped
@IfBuildProperty(name = "app.persistence.mongodb.enabled", stringValue = "true")
class MongoInitialAdminBootstrapWriteGate
    @Inject
    constructor(
        private val mongoClientProvider: Provider<MongoClient>,
        @ConfigProperty(name = "quarkus.mongodb.database") private val databaseName: String,
    ) : InitialAdminBootstrapWriteGate {
        // Direct client constructor is used by the standalone MongoDB integration harness.
        constructor(mongoClient: MongoClient, databaseName: String) :
            this(Provider { mongoClient }, databaseName)

        override fun reserveOnce() {
            val reservation =
                Document("_id", "initial-admin")
                    .append("createdAt", Date())
                    .append("state", "consumed")
            try {
                database()
                    .getCollection("admin_bootstrap")
                    .withWriteConcern(WriteConcern.JOURNALED)
                    .insertOne(reservation)
            } catch (error: MongoException) {
                if (error.code == 11000) {
                    throw InitialAdminAlreadyConfiguredException()
                }
                throw error
            }
        }

        override fun adminAlreadyExists(): Boolean =
            database()
                .getCollection("users")
                .find(Filters.eq("roles", "ADMIN"))
                .first() != null

        override fun persistCredentialDurably(credential: UserCredential) {
            // Matches the existing user_credentials schema; no raw password is written.
            val document =
                Document("_id", credential.userId.toString())
                    .append("passwordHash", credential.passwordHash)
            database()
                .getCollection("user_credentials")
                .withWriteConcern(WriteConcern.JOURNALED)
                .insertOne(document)
        }

        private fun database() = mongoClientProvider.get().getDatabase(databaseName)
    }
