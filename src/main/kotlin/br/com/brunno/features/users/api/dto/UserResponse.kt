package br.com.brunno.features.users.api.dto

import br.com.brunno.features.users.domain.User
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val name: String,
    val email: String
) {
    companion object {
        fun from(user: User): UserResponse =
            UserResponse(
                id = user.id,
                name = user.name,
                email = user.email
            )
    }
}
