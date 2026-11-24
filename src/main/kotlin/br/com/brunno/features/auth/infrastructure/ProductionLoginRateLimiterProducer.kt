package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.LoginAttemptRateLimiter
import com.mongodb.client.MongoClient
import io.quarkus.arc.profile.IfBuildProfile
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Produces
import jakarta.inject.Provider
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.Optional

/**
 * Production-only CDI boundary. Not @Startup: the privileged bootstrap CLI
 * also uses the prod build profile but never hosts an HTTP login endpoint.
 * Missing/invalid HMAC configuration fails closed when the login limiter is
 * resolved; it never silently falls back to a local/in-memory limiter.
 */
@Dependent
@IfBuildProfile("prod")
class ProductionLoginRateLimiterProducer {
    @Produces
    @ApplicationScoped
    fun limiter(
        mongoClientProvider: Provider<MongoClient>,
        @ConfigProperty(name = "quarkus.mongodb.database") databaseName: String,
        @ConfigProperty(name = "app.auth.rate-limit.hmac-key-base64") encodedSecret: Optional<String>,
        @ConfigProperty(name = "app.auth.rate-limit.identity.max-attempts", defaultValue = "5")
        identityMaxAttempts: Int,
        @ConfigProperty(name = "app.auth.rate-limit.identity.window-seconds", defaultValue = "300")
        identityWindowSeconds: Long,
        @ConfigProperty(name = "app.auth.rate-limit.origin.max-attempts", defaultValue = "30")
        originMaxAttempts: Int,
        @ConfigProperty(name = "app.auth.rate-limit.origin.window-seconds", defaultValue = "60")
        originWindowSeconds: Long,
    ): LoginAttemptRateLimiter {
        val settings =
            ProductionLoginRateLimitSettings.parse(
                encodedSecret.orElse(null),
                identityMaxAttempts,
                identityWindowSeconds,
                originMaxAttempts,
                originWindowSeconds,
            )
        val secret = settings.secretCopy()
        try {
            return MongoLoginAttemptRateLimiter(
                mongoClientProvider.get(),
                databaseName,
                settings.policy,
                secret,
            )
        } finally {
            secret.fill(0)
        }
    }
}
