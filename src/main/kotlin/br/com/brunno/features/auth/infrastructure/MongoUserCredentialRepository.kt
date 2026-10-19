package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.auth.domain.UserCredentialAlreadyExistsException
import br.com.brunno.features.auth.domain.UserCredentialRepository
import com.mongodb.MongoException
import io.quarkus.arc.properties.IfBuildProperty
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
@IfBuildProperty(
    name = "app.persistence.mongodb.enabled",
    stringValue = "true"
)
class MongoUserCredentialRepository :
    UserCredentialRepository,
    PanacheMongoRepositoryBase<MongoUserCredentialDocument, String> {

    override fun findByUserId(userId: UUID): UserCredential? =
        findById(userId.toString())
            ?.let(MongoUserCredentialMapper::toDomain)

    override fun create(
        credential: UserCredential
    ): UserCredential {
        val document = MongoUserCredentialMapper.toDocument(
            credential
        )

        try {
            persist(document)
        } catch (error: MongoException) {
            val isDuplicateId =
                error.code == 11000 &&
                        error.message.orEmpty().contains("_id_")

            if (isDuplicateId) {
                throw UserCredentialAlreadyExistsException(error)
            }

            throw error
        }

        return MongoUserCredentialMapper.toDomain(document)
    }
}
