package br.com.brunno.features.auth.api.dto

import br.com.brunno.features.auth.application.LoginCommand
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
) {
    fun toCommand(): LoginCommand {
        return LoginCommand(
            email = email,
            password = password
        )
    }
}
