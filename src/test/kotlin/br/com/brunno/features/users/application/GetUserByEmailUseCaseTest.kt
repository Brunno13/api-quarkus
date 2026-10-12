package br.com.brunno.features.users.application

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class GetUserByEmailUseCaseTest {

    @Test
    fun `returns user when normalized email exists`() {
        val user = User(
            id = UUID.randomUUID(),
            name = "Example User",
            email = "person@example.com"
        )

        val useCase = GetUserByEmailUseCase(
            repositoryWithUser(user)
        )

        val result = useCase.execute(
            "  PERSON@EXAMPLE.COM  "
        )

        assertEquals(user, result)
    }

    @Test
    fun `returns null when email does not exist`() {
        val existingUser = User(
            id = UUID.randomUUID(),
            name = "Example User",
            email = "person@example.com"
        )

        val useCase = GetUserByEmailUseCase(
            repositoryWithUser(existingUser)
        )

        val result = useCase.execute(
            "missing@example.com"
        )

        assertNull(result)
    }

    private fun repositoryWithUser(
        user: User?
    ): UserRepository =
        object : UserRepository {

            override fun findById(id: UUID): User? =
                throw AssertionError("Unexpected findById call")

            override fun findByEmail(email: String): User? =
                user?.takeIf { it.email == email }

            override fun create(user: User): User =
                throw AssertionError("Unexpected create call")
        }
}
