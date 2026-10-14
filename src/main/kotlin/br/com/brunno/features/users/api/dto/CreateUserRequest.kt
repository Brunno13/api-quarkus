package br.com.brunno.features.users.api.dto

import br.com.brunno.features.users.application.CreateUserCommand
import jakarta.validation.constraints.NotBlank

data class CreateUserRequest(
    @field:NotBlank
    val name: String,

    @field:NotBlank
    val email: String
) {
    fun toCommand(): CreateUserCommand =
        CreateUserCommand(
            name = name,
            email = email
        )
}
