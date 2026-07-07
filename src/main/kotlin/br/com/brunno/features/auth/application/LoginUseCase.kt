package br.com.brunno.features.auth.application

import br.com.brunno.features.auth.domain.UserRole
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.NotAuthorizedException

@ApplicationScoped
class LoginUseCase {

    fun execute(command: LoginCommand): LoginResult {
        val email = command.email.trim().lowercase()

        if (email != "admin@local.dev" || command.password != "admin123") {
            throw NotAuthorizedException("Invalid credentials")
        }

        return LoginResult(
            accessToken = "mock-access-token",
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(
                id = 1L,
                name = "Local Admin",
                email = email,
                roles = setOf(UserRole.ADMIN)
            )
        )
    }
}
