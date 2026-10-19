package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.domain.UserCredential
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class MongoUserCredentialMapperTest {

    @Test
    fun `maps user UUID to MongoDB document ID`() {
        val userId = UUID.randomUUID()
        val hash = "test-hash-not-a-real-password"

        val credential = UserCredential(
            userId = userId,
            passwordHash = hash
        )

        val document = MongoUserCredentialMapper.toDocument(
            credential
        )

        assertEquals(userId.toString(), document.id)
        assertEquals(hash, document.passwordHash)
    }

    @Test
    fun `restores credential from MongoDB document`() {
        val userId = UUID.randomUUID()
        val hash = "test-hash-not-a-real-password"

        val document = MongoUserCredentialDocument().apply {
            id = userId.toString()
            passwordHash = hash
        }

        val credential = MongoUserCredentialMapper.toDomain(
            document
        )

        assertEquals(userId, credential.userId)
        assertEquals(hash, credential.passwordHash)
    }

    @Test
    fun `rejects missing MongoDB document ID`() {
        val document = MongoUserCredentialDocument().apply {
            passwordHash = "test-hash"
        }

        assertThrows(IllegalArgumentException::class.java) {
            MongoUserCredentialMapper.toDomain(document)
        }
    }

    @Test
    fun `rejects invalid MongoDB document UUID`() {
        val document = MongoUserCredentialDocument().apply {
            id = "invalid-uuid"
            passwordHash = "test-hash"
        }

        assertThrows(IllegalArgumentException::class.java) {
            MongoUserCredentialMapper.toDomain(document)
        }
    }

    @Test
    fun `rejects blank stored password hash`() {
        val document = MongoUserCredentialDocument().apply {
            id = UUID.randomUUID().toString()
            passwordHash = ""
        }

        assertThrows(IllegalArgumentException::class.java) {
            MongoUserCredentialMapper.toDomain(document)
        }
    }
}
