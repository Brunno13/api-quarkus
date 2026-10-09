package br.com.brunno.features.auth.application

import br.com.brunno.features.auth.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LoginUseCaseTest {

    private val useCase = LoginUseCase()

    @Test
    fun `valid credentials return authenticated user`() {
        val result = useCase.execute(
            LoginCommand("admin@local.dev", "admin123")
        )

        assertEquals("mock-access-token", result.accessToken)
        assertEquals("Bearer", result.tokenType)
        assertEquals(900L, result.expiresIn)
        assertEquals(setOf(UserRole.ADMIN), result.user.roles)
    }

    @Test
    fun `email is normalized before authentication`() {
        val result = useCase.execute(
            LoginCommand(" ADMIN@LOCAL.DEV ", "admin123")
        )

        assertEquals("admin@local.dev", result.user.email)
    }

    @Test
    fun `invalid password is rejected`() {
        assertThrows(
            InvalidCredentialsException::class.java
        ) {
            useCase.execute(
                LoginCommand("admin@local.dev", "wrong")
            )
        }
    }

    @Test
    fun `unknown email is rejected`() {
        assertThrows(
            InvalidCredentialsException::class.java
        ) {
            useCase.execute(
                LoginCommand("unknown@local.dev", "admin123")
            )
        }
    }
}
