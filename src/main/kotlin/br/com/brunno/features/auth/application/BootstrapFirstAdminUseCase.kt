package br.com.brunno.features.auth.application

import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.users.application.InvalidUserEmailFormatException
import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserEmail
import br.com.brunno.features.users.domain.UserRole
import java.util.UUID

/**
 * Prepares the initial administrator and delegates the one-time, fail-closed
 * write to [InitialAdminBootstrapStore]. This use case is not a CDI/HTTP entrypoint.
 */
class BootstrapFirstAdminUseCase(
    private val store: InitialAdminBootstrapStore,
    private val passwordHasher: PasswordHasher,
) {
    fun execute(command: BootstrapFirstAdminCommand): User {
        val name = command.name.trim()
        require(name.isNotBlank()) { "User name must not be blank" }

        val email = UserEmail.normalize(command.email)
        require(email.isNotBlank()) { "User email must not be blank" }
        if (!UserEmail.isValid(email)) {
            throw InvalidUserEmailFormatException()
        }

        val hash =
            try {
                passwordHasher.hash(command.rawPassword)
            } catch (_: IllegalArgumentException) {
                throw InvalidInitialPasswordException()
            }

        val user =
            User(
                id = UUID.randomUUID(),
                name = name,
                email = email,
                roles = setOf(UserRole.ADMIN),
            )
        return store.createFirstAdmin(
            user = user,
            credential = UserCredential(user.id, hash),
        )
    }
}
