package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.InitialAdminAlreadyConfiguredException
import br.com.brunno.features.auth.application.InitialAdminBootstrapStore
import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import br.com.brunno.features.users.domain.UserRole
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped

/**
 * The credential is journal-acknowledged before its ADMIN user is persisted.
 * Reservation is consumed even if later operations fail: recovery is manual.
 */
@ApplicationScoped
@IfBuildProperty(name = "app.persistence.mongodb.enabled", stringValue = "true")
class ReservedInitialAdminBootstrapStore(
    private val gate: InitialAdminBootstrapWriteGate,
    private val userRepository: UserRepository
) : InitialAdminBootstrapStore {

    override fun createFirstAdmin(user: User, credential: UserCredential): User {
        require(user.roles == setOf(UserRole.ADMIN)) {
            "Initial administrator must have only the ADMIN role"
        }
        require(user.id == credential.userId) {
            "Bootstrap user and credential IDs must match"
        }

        gate.reserveOnce()
        if (gate.adminAlreadyExists()) {
            throw InitialAdminAlreadyConfiguredException()
        }

        // Never create the ADMIN document before the credential is durable.
        gate.persistCredentialDurably(credential)
        return userRepository.create(user)
    }
}
