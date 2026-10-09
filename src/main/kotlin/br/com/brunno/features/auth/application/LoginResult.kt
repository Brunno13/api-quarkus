package br.com.brunno.features.auth.application

import br.com.brunno.features.users.domain.UserRole

data class LoginResult(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: AuthenticatedUser
)

data class AuthenticatedUser(
    val id: Long,
    val name: String,
    val email: String,
    val roles: Set<UserRole>
)
