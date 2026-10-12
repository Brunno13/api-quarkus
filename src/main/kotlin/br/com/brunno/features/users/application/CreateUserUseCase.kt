package br.com.brunno.features.users.application

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserEmail
import br.com.brunno.features.users.domain.UserRepository
import br.com.brunno.features.users.domain.UserRole
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
@IfBuildProperty(
    name = "app.persistence.mongodb.enabled",
    stringValue = "true"
)
class CreateUserUseCase(
    private val repository: UserRepository
) {

    fun execute(command: CreateUserCommand): User {
        val name = command.name.trim()
        val email = UserEmail.normalize(command.email)

        require(name.isNotBlank()) {
            "User name must not be blank"
        }

        require(email.isNotBlank()) {
            "User email must not be blank"
        }

        require(UserEmail.isValid(email)) {
            "User email has an invalid format"
        }

        val user = User(
            id = UUID.randomUUID(),
            name = name,
            email = email,
            roles = setOf(UserRole.USER)
        )

        return repository.create(user)
    }
}
