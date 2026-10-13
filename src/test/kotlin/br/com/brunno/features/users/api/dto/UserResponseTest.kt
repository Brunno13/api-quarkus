package br.com.brunno.features.users.api.dto

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class UserResponseTest {

    @Test
    fun `maps user domain to API response`() {
        val id = UUID.randomUUID()

        val user = User(
            id = id,
            name = "Example User",
            email = "person@example.com",
            roles = setOf(UserRole.USER)
        )

        val response = UserResponse.from(user)

        assertEquals(id, response.id)
        assertEquals("Example User", response.name)
        assertEquals("person@example.com", response.email)
    }
}
