package br.com.brunno.features.users.application

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserEmail
import br.com.brunno.features.users.domain.UserRepository
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@IfBuildProperty(
    name = "app.persistence.mongodb.enabled",
    stringValue = "true"
)
class GetUserByEmailUseCase(
    private val repository: UserRepository
) {

    fun execute(email: String): User? =
        repository.findByEmail(UserEmail.normalize(email))
}
