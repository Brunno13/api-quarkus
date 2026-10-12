package br.com.brunno.features.users.application

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
@IfBuildProperty(
    name = "app.persistence.mongodb.enabled",
    stringValue = "true"
)
class GetUserByIdUseCase(
    private val repository: UserRepository
) {

    fun execute(id: UUID): User? =
        repository.findById(id)
}
