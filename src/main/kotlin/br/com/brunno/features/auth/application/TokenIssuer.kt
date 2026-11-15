package br.com.brunno.features.auth.application

import br.com.brunno.features.users.domain.User

/** Implementations sign tokens using an externally managed key. */
interface TokenIssuer {
    fun issue(user: User): IssuedAccessToken
}

class IssuedAccessToken(
    val accessToken: String,
    val expiresInSeconds: Long
) {
    init {
        require(accessToken.isNotBlank()) { "Access token must not be blank" }
        require(expiresInSeconds > 0) { "Access token lifetime must be positive" }
    }

    override fun toString(): String =
        "IssuedAccessToken(accessToken=[REDACTED], expiresInSeconds=$expiresInSeconds)"
}
