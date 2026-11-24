package br.com.brunno.features.auth.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LoginRateLimitContractTest {
    @Test
    fun `quota accepts positive limits and whole second windows`() {
        val quota = LoginRateLimitQuota(maxAttempts = 10, windowSeconds = 900)
        assertEquals(10, quota.maxAttempts)
        assertEquals(900L, quota.windowSeconds)
    }

    @Test
    fun `quota supports a one second window`() {
        assertEquals(1L, LoginRateLimitQuota(1, 1).windowSeconds)
    }

    @Test
    fun `quota supports a one day window`() {
        assertEquals(86_400L, LoginRateLimitQuota(1, 86_400).windowSeconds)
    }

    @Test
    fun `quota rejects zero attempts`() {
        assertThrows(IllegalArgumentException::class.java) {
            LoginRateLimitQuota(0, 60)
        }
    }

    @Test
    fun `quota rejects negative attempts`() {
        assertThrows(IllegalArgumentException::class.java) {
            LoginRateLimitQuota(-1, 60)
        }
    }

    @Test
    fun `quota rejects zero window`() {
        assertThrows(IllegalArgumentException::class.java) {
            LoginRateLimitQuota(1, 0)
        }
    }

    @Test
    fun `quota rejects negative window`() {
        assertThrows(IllegalArgumentException::class.java) {
            LoginRateLimitQuota(1, -60)
        }
    }

    @Test
    fun `quota rejects windows longer than one day`() {
        assertThrows(IllegalArgumentException::class.java) {
            LoginRateLimitQuota(1, 86_401)
        }
    }

    @Test
    fun `identity and origin quotas stay independent`() {
        val policy =
            LoginRateLimitPolicy(
                perIdentity = LoginRateLimitQuota(10, 900),
                perOrigin = LoginRateLimitQuota(60, 60),
            )
        assertEquals(10, policy.perIdentity.maxAttempts)
        assertEquals(60L, policy.perOrigin.windowSeconds)
    }

    @Test
    fun `allowed decision has no account or address details`() {
        assertSame(LoginAttemptDecision.Allowed, LoginAttemptDecision.Allowed)
    }

    @Test
    fun `rejected decision carries only a positive retry delay`() {
        assertEquals(17L, LoginAttemptDecision.Rejected(17).retryAfterSeconds)
    }

    @Test
    fun `rejected decision rejects nonpositive retry delays`() {
        assertThrows(IllegalArgumentException::class.java) {
            LoginAttemptDecision.Rejected(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LoginAttemptDecision.Rejected(-1)
        }
    }
}
