package br.com.brunno.features.auth.application

import br.com.brunno.features.users.domain.User

/** Application result, not an HTTP DTO. */
class AuthenticatedSession(
    val user: User,
    val token: IssuedAccessToken
) {
    override fun toString(): String =
        "AuthenticatedSession(user=[REDACTED], token=[REDACTED])"
}
