package br.com.brunno.features.auth.application

import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.auth.domain.UserCredentialAlreadyExistsException
import br.com.brunno.features.auth.domain.UserCredentialRepository
import br.com.brunno.features.users.domain.UserRepository
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
@IfBuildProperty(
    name = "app.persistence.mongodb.enabled",
    stringValue = "true"
)
class ProvisionUserCredentialUseCase(
    private val userRepository: UserRepository,
    private val credentialRepository: UserCredentialRepository,
    private val passwordHasher: PasswordHasher
) {

    fun execute(userId: UUID, rawPassword: String) {
        if (userRepository.findById(userId) == null) {
            throw UserNotFoundForCredentialProvisioningException()
        }

        if (credentialRepository.findByUserId(userId) != null) {
            throw UserCredentialAlreadyExistsException()
        }

        val passwordHash = try {
            passwordHasher.hash(rawPassword)
        } catch (_: IllegalArgumentException) {
            throw InvalidInitialPasswordException()
        }

        credentialRepository.create(UserCredential(userId, passwordHash))
    }
}
