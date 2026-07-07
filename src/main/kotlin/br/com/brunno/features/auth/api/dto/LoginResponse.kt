package br.com.brunno.features.auth.api.dto

import br.com.brunno.features.auth.application.LoginResult

data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: UserResponse
) {
    companion object {
        fun from(result: LoginResult): LoginResponse {
            return LoginResponse(
                accessToken = result.accessToken,
                tokenType = result.tokenType,
                expiresIn = result.expiresIn,
                user = UserResponse(
                    id = result.user.id,
                    name = result.user.name,
                    email = result.user.email,
                    roles = result.user.roles.map { it.name }.toSet()
                )
            )
        }
    }
}

data class UserResponse(
    val id: Long,
    val name: String,
    val email: String,
    val roles: Set<String>
)
