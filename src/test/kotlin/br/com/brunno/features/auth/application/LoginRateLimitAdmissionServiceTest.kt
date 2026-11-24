package br.com.brunno.features.auth.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoginRateLimitAdmissionServiceTest {
    @Test
    fun `allowed login reserves normalized email and verified client IP`() {
        val limiter = FakeLimiter()
        LoginRateLimitAdmissionService(limiter).reserve("  USER@EXAMPLE.TEST  ", "192.0.2.17")
        assertEquals(listOf("user@example.test" to "192.0.2.17"), limiter.calls)
    }

    @Test
    fun `rejection preserves delay without revealing rate limit dimension`() {
        val limiter = FakeLimiter(LoginAttemptDecision.Rejected(43))
        val error =
            assertThrows(LoginRateLimitExceededException::class.java) {
                LoginRateLimitAdmissionService(limiter).reserve("user@example.test", "192.0.2.17")
            }
        assertEquals(43L, error.retryAfterSeconds)
        assertEquals("Too many login attempts", error.message)
        assertFalse(error.toString().contains("user@example.test"))
        assertFalse(error.toString().contains("192.0.2.17"))
        assertEquals(1, limiter.calls.size)
    }

    @Test
    fun `invalid email fails generically without reaching the limiter`() {
        val limiter = FakeLimiter()
        val error =
            assertThrows(InvalidCredentialsException::class.java) {
                LoginRateLimitAdmissionService(limiter).reserve("invalid-email", "192.0.2.17")
            }
        assertEquals("Invalid credentials", error.message)
        assertTrue(limiter.calls.isEmpty())
    }

    @Test
    fun `empty verified client address fails closed`() {
        val limiter = FakeLimiter()
        assertThrows(IllegalArgumentException::class.java) {
            LoginRateLimitAdmissionService(limiter).reserve("user@example.test", " ")
        }
        assertTrue(limiter.calls.isEmpty())
    }

    @Test
    fun `oversized verified client address fails closed`() {
        val limiter = FakeLimiter()
        assertThrows(IllegalArgumentException::class.java) {
            LoginRateLimitAdmissionService(limiter).reserve("user@example.test", "x".repeat(65))
        }
        assertTrue(limiter.calls.isEmpty())
    }

    @Test
    fun `store failure is propagated rather than granting admission`() {
        val failure = IllegalStateException("rate limit storage unavailable")
        val limiter = FakeLimiter().apply { storeFailure = failure }
        val thrown =
            assertThrows(IllegalStateException::class.java) {
                LoginRateLimitAdmissionService(limiter).reserve("user@example.test", "192.0.2.17")
            }
        assertSame(failure, thrown)
        assertEquals(1, limiter.calls.size)
    }

    @Test
    fun `exception refuses zero retry after`() {
        assertThrows(IllegalArgumentException::class.java) {
            LoginRateLimitExceededException(0)
        }
    }

    private class FakeLimiter(
        private val decision: LoginAttemptDecision = LoginAttemptDecision.Allowed,
    ) : LoginAttemptRateLimiter {
        val calls = mutableListOf<Pair<String, String>>()
        var storeFailure: RuntimeException? = null

        override fun reserveAttempt(
            normalizedEmail: String,
            clientIp: String,
        ): LoginAttemptDecision {
            calls += normalizedEmail to clientIp
            storeFailure?.let { throw it }
            return decision
        }
    }
}
