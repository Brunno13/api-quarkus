package br.com.brunno.features.auth.application

/** A transport-neutral admission decision; it never reveals which quota was hit. */
sealed interface LoginAttemptDecision {
    data object Allowed : LoginAttemptDecision

    /** Strictly positive number of seconds to send as the HTTP Retry-After value. */
    data class Rejected(val retryAfterSeconds: Long) : LoginAttemptDecision {
        init {
            require(retryAfterSeconds > 0) { "retryAfterSeconds must be positive" }
        }
    }
}

/**
 * Reserves capacity BEFORE password verification and JWT signing.
 *
 * The production adapter must perform atomic accounting in a shared store,
 * including across concurrent requests and multiple application instances.
 * Both identity and origin quotas count admitted attempts, regardless of outcome;
 * this avoids a check-then-increment race during expensive BCrypt verification.
 *
 * The caller provides an email normalized with UserEmail.normalize and a client
 * IP selected from the transport's verified peer/trusted-proxy configuration.
 * Never trust arbitrary Forwarded or X-Forwarded-For request headers.
 * The infrastructure adapter must avoid persisting raw email/IP identifiers
 * and must fail closed when admission cannot be determined.
 *
 * The production implementation is supplied by a CDI producer in the prod profile.
 */
interface LoginAttemptRateLimiter {
    fun reserveAttempt(normalizedEmail: String, clientIp: String): LoginAttemptDecision
}
