package br.com.brunno.features.auth.domain

import java.util.UUID

class UserCredential(
    val userId: UUID,
    val passwordHash: String
) {
    init {
        require(passwordHash.isNotBlank()) {
            "Password hash must not be blank"
        }
    }

    override fun toString(): String =
        "UserCredential(userId=$userId, passwordHash=[REDACTED])"
}
