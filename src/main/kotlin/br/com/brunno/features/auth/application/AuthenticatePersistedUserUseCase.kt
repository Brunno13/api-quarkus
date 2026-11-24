package br.com.brunno.features.auth.application

import br.com.brunno.features.auth.domain.UserCredentialRepository
import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserEmail
import br.com.brunno.features.users.domain.UserRepository
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@IfBuildProperty(
    name = "app.persistence.mongodb.enabled",
    stringValue = "true",
)
class AuthenticatePersistedUserUseCase(
    private val userRepository: UserRepository,
    private val credentialRepository: UserCredentialRepository,
    private val passwordHasher: PasswordHasher,
) {
    // This is not an account credential. It supplies BCrypt work for missing accounts.
    private val dummyHash = passwordHasher.hash("authentication-timing-placeholder")

    fun execute(
        email: String,
        rawPassword: String,
    ): User {
        val normalizedEmail = UserEmail.normalize(email)
        val user =
            if (UserEmail.isValid(normalizedEmail)) {
                userRepository.findByEmail(normalizedEmail)
            } else {
                null
            }

        val storedHash =
            user
                ?.let { credentialRepository.findByUserId(it.id) }
                ?.passwordHash

        // Always verify a hash, including when the user or credential is absent.
        val passwordMatches =
            passwordHasher.verify(
                rawPassword,
                storedHash ?: dummyHash,
            )

        if (user == null || storedHash == null || !passwordMatches) {
            throw InvalidCredentialsException()
        }

        return user
    }
}
