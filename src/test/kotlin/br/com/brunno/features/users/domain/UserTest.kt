package br.com.brunno.features.users.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class UserTest {

    @Test
    fun `new user has USER role by default`() {
        val id = UUID.randomUUID()

        val user = User(
            id = id,
            name = "Example User",
            email = "user@example.com"
        )

        assertEquals(id, user.id)
        assertEquals("Example User", user.name)
        assertEquals("user@example.com", user.email)
        assertEquals(setOf(UserRole.USER), user.roles)
    }

    @Test
    fun `user supports explicitly assigned roles`() {
        val roles = setOf(UserRole.USER, UserRole.ADMIN)

        val user = User(
            id = UUID.randomUUID(),
            name = "Example Admin",
            email = "admin@example.com",
            roles = roles
        )

        assertEquals(roles, user.roles)
    }
}
