package br.com.brunno.shared.error

import br.com.brunno.features.auth.application.LoginRateLimitExceededException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class LoginRateLimitExceededExceptionMapperTest {
    @Test
    fun `rejection maps to generic 429 with numeric retry after header`() {
        val response = LoginRateLimitExceededExceptionMapper()
            .toResponse(LoginRateLimitExceededException(37))
        assertEquals(429, response.status)
        assertEquals("37", response.getHeaderString("Retry-After"))
        assertEquals("application/json", response.mediaType.toString())
    }

    @Test
    fun `rejection body uses generic API error without account identifiers`() {
        val response = LoginRateLimitExceededExceptionMapper()
            .toResponse(LoginRateLimitExceededException(37))
        val body = response.entity as ApiErrorResponse
        assertEquals(429, body.status)
        assertEquals("Too Many Requests", body.title)
        assertEquals("Too many login attempts", body.detail)
        assertFalse(body.toString().contains("@example.test"))
    }
}
