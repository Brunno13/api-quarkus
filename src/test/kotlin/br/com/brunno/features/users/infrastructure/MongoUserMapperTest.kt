package br.com.brunno.features.users.infrastructure

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class MongoUserMapperTest {

    @Test
    fun `maps domain user to MongoDB document`() {
        val id = UUID.randomUUID()

        val user = User(
            id = id,
            name = "Example Admin",
            email = "admin@example.com",
            roles = setOf(UserRole.USER, UserRole.ADMIN)
        )

        val document = MongoUserMapper.toDocument(user)

        assertEquals(id.toString(), document.id)
        assertEquals(user.name, document.name)
        assertEquals(user.email, document.email)
        assertEquals(listOf("ADMIN", "USER"), document.roles)
    }

    @Test
    fun `restores domain user from MongoDB document`() {
        val id = UUID.randomUUID()

        val document = MongoUserDocument().apply {
            this.id = id.toString()
            name = "Example User"
            email = "user@example.com"
            roles = listOf("USER")
        }

        val user = MongoUserMapper.toDomain(document)

        assertEquals(id, user.id)
        assertEquals(document.name, user.name)
        assertEquals(document.email, user.email)
        assertEquals(setOf(UserRole.USER), user.roles)
    }

    @Test
    fun `rejects document without identifier`() {
        val document = MongoUserDocument()

        assertThrows(IllegalArgumentException::class.java) {
            MongoUserMapper.toDomain(document)
        }
    }

    @Test
    fun `rejects unknown user role`() {
        val document = MongoUserDocument().apply {
            id = UUID.randomUUID().toString()
            name = "Example User"
            email = "user@example.com"
            roles = listOf("UNKNOWN_ROLE")
        }

        assertThrows(IllegalArgumentException::class.java) {
            MongoUserMapper.toDomain(document)
        }
    }
}
