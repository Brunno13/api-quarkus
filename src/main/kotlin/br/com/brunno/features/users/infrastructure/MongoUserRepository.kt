package br.com.brunno.features.users.infrastructure

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import io.quarkus.arc.properties.IfBuildProperty
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

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
        find("email", email)
            .firstResult()
            ?.let(MongoUserMapper::toDomain)

    override fun create(user: User): User {
        val document = MongoUserMapper.toDocument(user)

        persist(document)

        return MongoUserMapper.toDomain(document)
    }
}
