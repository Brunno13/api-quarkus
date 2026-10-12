package br.com.brunno.features.users.infrastructure

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import io.quarkus.arc.properties.IfBuildProperty
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepositoryBase
import com.mongodb.MongoException
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import br.com.brunno.features.users.domain.UserEmail
import br.com.brunno.features.users.domain.UserEmailAlreadyExistsException

@ApplicationScoped
@IfBuildProperty(
    name = "app.persistence.mongodb.enabled",
    stringValue = "true"
)
class MongoUserRepository :
    UserRepository,
    PanacheMongoRepositoryBase<MongoUserDocument, String> {

    override fun findById(id: UUID): User? =
        findById(id.toString())
            ?.let(MongoUserMapper::toDomain)

    override fun findByEmail(email: String): User? =
        find("email", UserEmail.normalize(email))
            .firstResult()
            ?.let(MongoUserMapper::toDomain)

    override fun create(user: User): User {
        val document = MongoUserMapper.toDocument(user)

        try {
            persist(document)
        } catch (error: MongoException) {
            val isDuplicateEmail =
                error.code == 11000 &&
                    error.message.orEmpty().contains(
                        "ux_users_email"
                    )

            if (isDuplicateEmail) {
                throw UserEmailAlreadyExistsException(error)
            }

            throw error
        }

        return MongoUserMapper.toDomain(document)
    }
}
