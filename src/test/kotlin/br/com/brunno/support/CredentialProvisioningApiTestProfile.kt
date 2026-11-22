package br.com.brunno.support

import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.auth.domain.UserCredentialAlreadyExistsException
import br.com.brunno.features.auth.domain.UserCredentialRepository
import io.quarkus.test.junit.QuarkusTestProfile
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import java.util.UUID

class CredentialProvisioningApiTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> =
        mapOf(
            "app.persistence.mongodb.enabled" to "true",
            "quarkus.mongodb.devservices.enabled" to "false",
            "quarkus.mongodb.health.enabled" to "false",
            "mp.jwt.verify.issuer" to "api-quarkus",
        )

    override fun getEnabledAlternatives(): Set<Class<*>> =
        setOf(
            TestUserRepository::class.java,
            TestUserCredentialRepository::class.java,
        )
}

@Alternative
@ApplicationScoped
class TestUserCredentialRepository : UserCredentialRepository {
    private val credentials = mutableMapOf<UUID, UserCredential>()

    fun reset() {
        credentials.clear()
    }

    override fun findByUserId(userId: UUID): UserCredential? = credentials[userId]

    override fun create(credential: UserCredential): UserCredential {
        if (credentials.containsKey(credential.userId)) {
            throw UserCredentialAlreadyExistsException()
        }

        credentials[credential.userId] = credential
        return credential
    }
}
