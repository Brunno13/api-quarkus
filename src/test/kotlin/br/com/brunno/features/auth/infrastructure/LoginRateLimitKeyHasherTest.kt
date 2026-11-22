package br.com.brunno.features.auth.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoginRateLimitKeyHasherTest {
    private val secret = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun `same canonical identity and bucket produce stable digest`() {
        val hasher = LoginRateLimitKeyHasher(secret)
        assertEquals(
            hasher.keyFor("identity", "person@example.test", 21),
            hasher.keyFor("identity", "person@example.test", 21),
        )
    }

    @Test
    fun `identity and origin are domain separated`() {
        val hasher = LoginRateLimitKeyHasher(secret)
        assertNotEquals(
            hasher.keyFor("identity", "same-value", 21),
            hasher.keyFor("origin", "same-value", 21),
        )
    }

    @Test
    fun `different buckets get different document keys`() {
        val hasher = LoginRateLimitKeyHasher(secret)
        assertNotEquals(
            hasher.keyFor("identity", "person@example.test", 21),
            hasher.keyFor("identity", "person@example.test", 22),
        )
    }

    @Test
    fun `different secrets get different document keys`() {
        assertNotEquals(
            LoginRateLimitKeyHasher(secret).keyFor("identity", "person@example.test", 21),
            LoginRateLimitKeyHasher(ByteArray(32) { 42 }).keyFor(
                "identity",
                "person@example.test",
                21,
            ),
        )
    }

    @Test
    fun `database key never embeds raw email or origin`() {
        val hasher = LoginRateLimitKeyHasher(secret)
        val identity = hasher.keyFor("identity", "person@example.test", 21)
        val origin = hasher.keyFor("origin", "192.0.2.41", 21)
        assertFalse(identity.contains("person@example.test"))
        assertFalse(origin.contains("192.0.2.41"))
        assertTrue(identity.matches(Regex("v1:identity:21:[0-9a-f]{64}")))
        assertTrue(origin.matches(Regex("v1:origin:21:[0-9a-f]{64}")))
    }

    @Test
    fun `HMAC key requires at least 32 bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            LoginRateLimitKeyHasher(ByteArray(31))
        }
        LoginRateLimitKeyHasher(ByteArray(32))
    }

    @Test
    fun `key derivation refuses unsupported dimension or empty identifier`() {
        val hasher = LoginRateLimitKeyHasher(secret)
        assertThrows(IllegalArgumentException::class.java) {
            hasher.keyFor("account", "person@example.test", 21)
        }
        assertThrows(IllegalArgumentException::class.java) {
            hasher.keyFor("identity", "   ", 21)
        }
    }

    @Test
    fun `caller mutating original key does not change derived document keys`() {
        val mutableSecret = secret.copyOf()
        val hasher = LoginRateLimitKeyHasher(mutableSecret)
        val before = hasher.keyFor("identity", "person@example.test", 21)
        mutableSecret.fill(0)
        assertEquals(before, hasher.keyFor("identity", "person@example.test", 21))
    }
}
