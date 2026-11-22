package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.LoginRateLimitPolicy
import br.com.brunno.features.auth.application.LoginRateLimitQuota
import java.util.Base64

/**
 * Validated runtime settings for the production login limiter. There is no
 * embedded/default HMAC key and none of the failure messages prints its value.
 * The CLI bootstrap can run without these settings because it never resolves
 * the production LoginAttemptRateLimiter bean.
 */
internal class ProductionLoginRateLimitSettings private constructor(
    val policy: LoginRateLimitPolicy,
    private val secret: ByteArray,
) {
    fun secretCopy(): ByteArray = secret.copyOf()

    override fun toString(): String = "ProductionLoginRateLimitSettings(policy=$policy, secret=[REDACTED])"

    companion object {
        fun parse(
            encodedSecret: String?,
            identityMaxAttempts: Int,
            identityWindowSeconds: Long,
            originMaxAttempts: Int,
            originWindowSeconds: Long,
        ): ProductionLoginRateLimitSettings {
            val text = encodedSecret?.trim()
            require(!text.isNullOrEmpty()) {
                "Login rate-limit HMAC key is not configured"
            }
            // Keep accidental oversized environment values out of the decoder.
            require(text.length <= 172) {
                "Login rate-limit HMAC key encoding is invalid"
            }
            val decoded =
                try {
                    Base64.getDecoder().decode(text)
                } catch (_: IllegalArgumentException) {
                    throw IllegalArgumentException("Login rate-limit HMAC key encoding is invalid")
                }
            try {
                require(decoded.size in 32..128) {
                    "Login rate-limit HMAC key must contain 32 to 128 bytes"
                }
                return ProductionLoginRateLimitSettings(
                    policy =
                        LoginRateLimitPolicy(
                            perIdentity =
                                LoginRateLimitQuota(
                                    identityMaxAttempts,
                                    identityWindowSeconds,
                                ),
                            perOrigin =
                                LoginRateLimitQuota(
                                    originMaxAttempts,
                                    originWindowSeconds,
                                ),
                        ),
                    secret = decoded.copyOf(),
                )
            } finally {
                decoded.fill(0)
            }
        }
    }
}
