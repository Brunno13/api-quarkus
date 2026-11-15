package br.com.brunno.features.auth.application

import br.com.brunno.features.auth.api.dto.LoginRequest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoginCredentialsRedactionTest {

    @Test
    fun `login request string does not reveal email or password`() {
        val email = "private@example.com"
        val password = "sensitive-password-2026"
        val rendered = LoginRequest(email, password).toString()

        assertFalse(rendered.contains(email))
        assertFalse(rendered.contains(password))
        assertTrue(rendered.contains("[REDACTED]"))
    }

    @Test
    fun `login command string does not reveal email or password`() {
        val email = "private@example.com"
        val password = "sensitive-password-2026"
        val rendered = LoginCommand(email, password).toString()

        assertFalse(rendered.contains(email))
        assertFalse(rendered.contains(password))
        assertTrue(rendered.contains("[REDACTED]"))
    }
}
