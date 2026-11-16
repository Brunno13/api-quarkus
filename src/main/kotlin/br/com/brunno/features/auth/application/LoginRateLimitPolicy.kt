package br.com.brunno.features.auth.application

/**
 * The rate limit uses fixed windows expressed in whole seconds.
 * Values are supplied by the production configuration; this policy defines
 * the quotas but does not implement throttling on its own.
 */
data class LoginRateLimitQuota(
    val maxAttempts: Int,
    val windowSeconds: Long
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(windowSeconds in 1..86_400) {
            "windowSeconds must be between 1 and 86400"
        }
    }
}

/** Independent quotas for an identity and an originating client address. */
data class LoginRateLimitPolicy(
    val perIdentity: LoginRateLimitQuota,
    val perOrigin: LoginRateLimitQuota
)
