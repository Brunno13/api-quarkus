package br.com.brunno.features.auth.infrastructure

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BcryptPasswordHasherTest {

    private val hasher = BcryptPasswordHasher()

    @Test
    fun `correct password matches and incorrect password fails`() {
        val password = "Example-Password-2026!"
        val hash = hasher.hash(password)

        assertNotEquals(password, hash)
        assertTrue(hasher.verify(password, hash))
        assertFalse(hasher.verify("Wrong-Password", hash))
    }

    @Test
    fun `same password produces different salted hashes`() {
        val password = "Example-Password-2026!"

        val first = hasher.hash(password)
        val second = hasher.hash(password)

        assertNotEquals(first, second)
        assertTrue(hasher.verify(password, first))
        assertTrue(hasher.verify(password, second))
    }

    @Test
    fun `blank passwords are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            hasher.hash("   ")
        }

        assertFalse(hasher.verify("   ", hasher.hash("Valid-Password")))
    }

    @Test
    fun `passwords exceeding 72 UTF8 bytes are rejected`() {
        val password = "á".repeat(37) // 74 bytes in UTF-8

        assertThrows(IllegalArgumentException::class.java) {
            hasher.hash(password)
        }

        assertFalse(
            hasher.verify(password, hasher.hash("Valid-Password"))
        )
    }

    @Test
    fun `malformed stored hashes are rejected`() {
        val password = "Valid-Password"

        assertFalse(
            hasher.verify(password, "not-a-bcrypt-hash")
        )

        assertFalse(
            hasher.verify(password, "")
        )

        assertFalse(
            hasher.verify(password, "\$2a\$12\$invalid")
        )
    }
}
