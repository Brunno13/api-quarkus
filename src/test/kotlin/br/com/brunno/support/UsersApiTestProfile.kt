package br.com.brunno.support

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import br.com.brunno.features.users.domain.UserEmailAlreadyExistsException
import io.quarkus.test.junit.QuarkusTestProfile
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import java.util.UUID

class UsersApiTestProfile : QuarkusTestProfile {

    override fun getConfigOverrides(): Map<String, String> =
        mapOf(
            "app.persistence.mongodb.enabled" to "true",
            "quarkus.mongodb.devservices.enabled" to "false",
            "quarkus.mongodb.health.enabled" to "false",
            "mp.jwt.verify.issuer" to "api-quarkus"
        )

    override fun getEnabledAlternatives(): Set<Class<*>> =
        setOf(TestUserRepository::class.java)
}

@Alternative
@ApplicationScoped
class TestUserRepository : UserRepository {

    companion object {
        val EXISTING_USER = User(
            id = UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
            ),
            name = "API Test User",
            email = "api-test@example.com"
        )
    }

    override fun findById(id: UUID): User? =
        EXISTING_USER.takeIf { it.id == id }

    override fun findByEmail(email: String): User? = null

    override fun create(user: User): User {
        if (user.email == "existing@example.com") {
            throw UserEmailAlreadyExistsException()
        }

        return user
    }
}
