package br.com.brunno.features.users.application

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import br.com.brunno.features.users.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class CreateUserUseCaseTest {

    private fun useCaseWithoutWrites(): CreateUserUseCase =
        CreateUserUseCase(
            object : UserRepository {
                override fun findById(id: UUID): User? = null

                override fun findByEmail(email: String): User? = null

                override fun create(user: User): User {
                    throw AssertionError(
                        "Repository must not be called for invalid input"
                    )
                }
            }
        )

    @Test
    fun `creates user with normalized data and default USER role`() {
        var persistedUser: User? = null

        val repository = object : UserRepository {
            override fun findById(id: UUID): User? = null

            override fun findByEmail(email: String): User? = null

            override fun create(user: User): User {
                persistedUser = user
                return user
            }
        }

        val useCase = CreateUserUseCase(repository)

        val result = useCase.execute(
            CreateUserCommand(
                name = "  Example User  ",
                email = "  PERSON@EXAMPLE.COM  "
            )
        )

        assertEquals("Example User", result.name)
        assertEquals("person@example.com", result.email)
        assertEquals(setOf(UserRole.USER), result.roles)
        assertEquals(result, persistedUser)
    }

    @Test
    fun `rejects blank user name`() {
        val useCase = useCaseWithoutWrites()

        val error = assertThrows(IllegalArgumentException::class.java) {
            useCase.execute(
                CreateUserCommand(
                    name = "   ",
                    email = "person@example.com"
                )
            )
        }

        assertEquals("User name must not be blank", error.message)
    }

    @Test
    fun `rejects blank user email`() {
        val useCase = useCaseWithoutWrites()

        val error = assertThrows(IllegalArgumentException::class.java) {
            useCase.execute(
                CreateUserCommand(
                    name = "Example User",
                    email = "   "
                )
            )
        }

        assertEquals("User email must not be blank", error.message)
    }

    @Test
    fun `rejects malformed user emails before persistence`() {
        val useCase = useCaseWithoutWrites()

        val invalidEmails = listOf(
            "invalid-email",
            "person@@example.com",
            "person@example",
            "person name@example.com"
        )

        invalidEmails.forEach { email ->
            val error = assertThrows(
                InvalidUserEmailFormatException::class.java
            ) {
                useCase.execute(
                    CreateUserCommand(
                        name = "Example User",
                        email = email
                    )
                )
            }

            assertEquals(
                "User email has an invalid format",
                error.message
            )
        }
    }
}
