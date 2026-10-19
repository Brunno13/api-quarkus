package br.com.brunno.features.auth.domain

import java.util.UUID

interface UserCredentialRepository {

    fun findByUserId(userId: UUID): UserCredential?

    fun create(credential: UserCredential): UserCredential
}
