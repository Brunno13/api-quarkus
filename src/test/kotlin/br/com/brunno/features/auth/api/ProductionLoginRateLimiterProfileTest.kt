package br.com.brunno.features.auth.api

import br.com.brunno.features.auth.application.LoginAttemptRateLimiter
import io.quarkus.arc.Arc
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@QuarkusTest
class ProductionLoginRateLimiterProfileTest {
    @Test
    fun `production Mongo limiter is not injectable in the test profile`() {
        assertFalse(Arc.container().instance(LoginAttemptRateLimiter::class.java).isAvailable)
    }
}
