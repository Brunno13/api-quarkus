package br.com.brunno.features.auth.application

import br.com.brunno.features.users.domain.UserEmail
import io.quarkus.arc.profile.IfBuildProfile
import jakarta.enterprise.context.ApplicationScoped

/**
 * Admission boundary to be called BEFORE persisted credential verification.
 * Available through CDI only in production; the offline admin bootstrap does not invoke it.
 * Neither a missing MongoDB counter nor an invalid source IP may grant admission.
 */
@ApplicationScoped
@IfBuildProfile("prod")
class LoginRateLimitAdmissionService(
    private val limiter: LoginAttemptRateLimiter
) {
    fun reserve(email: String, verifiedClientIp: String) {
        val normalizedEmail = UserEmail.normalize(email)
        if (!UserEmail.isValid(normalizedEmail)) {
            throw InvalidCredentialsException()
        }
        require(verifiedClientIp.isNotBlank() && verifiedClientIp.length <= 64) {
            "A verified client IP address is required"
        }
        when (val decision = limiter.reserveAttempt(normalizedEmail, verifiedClientIp)) {
            LoginAttemptDecision.Allowed -> Unit
            is LoginAttemptDecision.Rejected ->
                throw LoginRateLimitExceededException(decision.retryAfterSeconds)
        }
        // Operational failures from the store deliberately propagate (fail closed).
    }
}
