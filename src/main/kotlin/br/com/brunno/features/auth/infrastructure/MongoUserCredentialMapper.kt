package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.domain.UserCredential
import java.util.UUID

object MongoUserCredentialMapper {

    fun toDocument(
        credential: UserCredential
    ): MongoUserCredentialDocument =
        MongoUserCredentialDocument().apply {
            id = credential.userId.toString()
            passwordHash = credential.passwordHash
        }

    fun toDomain(
        document: MongoUserCredentialDocument
    ): UserCredential {
        val id = requireNotNull(document.id) {
            "MongoDB credential document ID cannot be null"
        }

        return UserCredential(
            userId = UUID.fromString(id),
            passwordHash = document.passwordHash
        )
    }
}
