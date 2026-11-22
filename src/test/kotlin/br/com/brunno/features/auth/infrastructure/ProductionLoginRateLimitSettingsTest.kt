package br.com.brunno.features.auth.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class ProductionLoginRateLimitSettingsTest {
    private val secret = ByteArray(32) { (it + 3).toByte() }
    private val encoded = Base64.getEncoder().encodeToString(secret)

    private fun parse(
        key: String? = encoded,
        identityMax: Int = 5,
        identityWindow: Long = 300,
        originMax: Int = 30,
        originWindow: Long = 60,
    ) = ProductionLoginRateLimitSettings.parse(
        key,
        identityMax,
        identityWindow,
        originMax,
        originWindow,
    )

    @Test
    fun `valid external key and quotas produce the expected policy`() {
        val settings = parse()
        assertEquals(5, settings.policy.perIdentity.maxAttempts)
        assertEquals(300L, settings.policy.perIdentity.windowSeconds)
        assertEquals(30, settings.policy.perOrigin.maxAttempts)
        assertEquals(60L, settings.policy.perOrigin.windowSeconds)
        assertTrue(settings.secretCopy().contentEquals(secret))
    }

    @Test
    fun `missing secret fails closed`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                parse(key = null)
            }
        assertEquals("Login rate-limit HMAC key is not configured", failure.message)
    }

    @Test
    fun `blank secret fails closed`() {
        assertThrows(IllegalArgumentException::class.java) { parse(key = "   ") }
    }

    @Test
    fun `malformed base64 secret fails without echoing its contents`() {
        val invalid = "SENSITIVE_NOT_BASE64!"
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                parse(key = invalid)
            }
        assertEquals("Login rate-limit HMAC key encoding is invalid", failure.message)
        assertFalse(failure.toString().contains(invalid))
    }

    @Test
    fun `HMAC key shorter than 256 bits is rejected`() {
        val short = Base64.getEncoder().encodeToString(ByteArray(31))
        assertThrows(IllegalArgumentException::class.java) { parse(key = short) }
    }

    @Test
    fun `oversized key is rejected`() {
        val long = Base64.getEncoder().encodeToString(ByteArray(129))
        assertThrows(IllegalArgumentException::class.java) { parse(key = long) }
    }

    @Test
    fun `invalid identity quota is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parse(identityMax = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parse(identityWindow = 86_401)
        }
    }

    @Test
    fun `invalid origin quota is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parse(originMax = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parse(originWindow = 0)
        }
    }

    @Test
    fun `key bytes cannot be changed by callers and do not appear in strings`() {
        val settings = parse()
        val first = settings.secretCopy()
        first.fill(0)
        assertTrue(settings.secretCopy().contentEquals(secret))
        assertNotEquals(first.toList(), settings.secretCopy().toList())
        assertTrue(settings.toString().contains("[REDACTED]"))
        assertFalse(settings.toString().contains(encoded))
    }
}
