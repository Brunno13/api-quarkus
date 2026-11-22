package br.com.brunno.features.auth.api.dto

import br.com.brunno.features.auth.application.AuthenticatedSession

/** Separate from the dev/test mock DTO, whose user ID is a Long. */
data class PersistedLoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: PersistedAuthenticatedUserResponse,
) {
    override fun toString(): String = "PersistedLoginResponse(accessToken=[REDACTED], user=[REDACTED])"

    companion object {
        fun from(session: AuthenticatedSession): PersistedLoginResponse =
            PersistedLoginResponse(
                accessToken = session.token.accessToken,
                tokenType = "Bearer",
                expiresIn = session.token.expiresInSeconds,
                user =
                    PersistedAuthenticatedUserResponse(
                        id = session.user.id.toString(),
                        name = session.user.name,
                        email = session.user.email,
                        roles =
                            session.user.roles
                                .map { it.name }
                                .toSet(),
                    ),
            )
    }
}

data class PersistedAuthenticatedUserResponse(
    val id: String,
    val name: String,
    val email: String,
    val roles: Set<String>,
)
