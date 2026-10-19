package br.com.brunno.features.auth.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class UserCredentialTest {

    @Test
    fun `credential references user by UUID`() {
        val userId = UUID.randomUUID()
        val hash = "stored-hash-for-test-only"

        val credential = UserCredential(
            userId = userId,
            passwordHash = hash
        )

        assertEquals(userId, credential.userId)
        assertEquals(hash, credential.passwordHash)
    }

    @Test
    fun `blank password hashes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            UserCredential(UUID.randomUUID(), "")
        }

        assertThrows(IllegalArgumentException::class.java) {
            UserCredential(UUID.randomUUID(), "   ")
        }
    }

    @Test
    fun `credential string representation never exposes hash`() {
        val hash = "sensitive-stored-hash"

        val credential = UserCredential(
            userId = UUID.randomUUID(),
            passwordHash = hash
        )

        val representation = credential.toString()

        assertFalse(representation.contains(hash))
        assertTrue(representation.contains("[REDACTED]"))
    }
}
