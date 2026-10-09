package br.com.brunno.features.users.domain

import java.util.UUID

interface UserRepository {

    fun findById(id: UUID): User?

    fun findByEmail(email: String): User?

    fun create(user: User): User
}
