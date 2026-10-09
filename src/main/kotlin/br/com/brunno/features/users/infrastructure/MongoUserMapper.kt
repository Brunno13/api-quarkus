package br.com.brunno.features.users.infrastructure

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRole
import java.util.UUID

object MongoUserMapper {

    fun toDocument(user: User): MongoUserDocument =
        MongoUserDocument().apply {
            id = user.id.toString()
            name = user.name
            email = user.email
            roles = user.roles.map { it.name }.sorted()
        }

    fun toDomain(document: MongoUserDocument): User {
        val id = requireNotNull(document.id) {
            "MongoDB user document ID cannot be null"
        }

        return User(
            id = UUID.fromString(id),
            name = document.name,
            email = document.email,
            roles = document.roles
                .map(UserRole::valueOf)
                .toSet()
        )
    }
}
