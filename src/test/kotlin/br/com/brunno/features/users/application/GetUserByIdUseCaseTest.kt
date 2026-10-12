package br.com.brunno.features.users.application

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class GetUserByIdUseCaseTest {

    @Test
    fun `returns user when id exists`() {
        val user = User(
            id = UUID.randomUUID(),
            name = "Example User",
            email = "person@example.com"
        )

        val useCase = GetUserByIdUseCase(
            repositoryWithUser(user)
        )

        val result = useCase.execute(user.id)

        assertEquals(user, result)
    }

    @Test
    fun `returns null when id does not exist`() {
        val existingUser = User(
            id = UUID.randomUUID(),
            name = "Example User",
            email = "person@example.com"
        )

        val useCase = GetUserByIdUseCase(
            repositoryWithUser(existingUser)
        )

        val result = useCase.execute(UUID.randomUUID())

        assertNull(result)
    }

    private fun repositoryWithUser(
        user: User?
    ): UserRepository =
        object : UserRepository {

            override fun findById(id: UUID): User? =
                user?.takeIf { it.id == id }

            override fun findByEmail(email: String): User? =
                throw AssertionError("Unexpected findByEmail call")

            override fun create(user: User): User =
                throw AssertionError("Unexpected create call")
        }
}
