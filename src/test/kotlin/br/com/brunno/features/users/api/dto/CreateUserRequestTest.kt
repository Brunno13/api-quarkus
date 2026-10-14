package br.com.brunno.features.users.api.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CreateUserRequestTest {

    @Test
    fun `maps request to create user command`() {
        val request = CreateUserRequest(
            name = "Example User",
            email = "person@example.com"
        )

        val command = request.toCommand()

        assertEquals("Example User", command.name)
        assertEquals("person@example.com", command.email)
    }
}
