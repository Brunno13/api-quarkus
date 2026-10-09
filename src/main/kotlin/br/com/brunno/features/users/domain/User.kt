package br.com.brunno.features.users.domain

import java.util.UUID

data class User(
    val id: UUID,
    val name: String,
    val email: String,
    val roles: Set<UserRole> = setOf(UserRole.USER)
)
