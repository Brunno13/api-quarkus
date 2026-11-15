package br.com.brunno.features.auth.application

import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.auth.domain.UserCredentialRepository
import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import br.com.brunno.features.users.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class AuthenticatePersistedUserUseCaseTest {

    private val user = User(
        id = UUID.fromString("00000000-0000-0000-0000-000000000123"),
        name = "Example User",
        email = "person@example.com",
        roles = setOf(UserRole.USER)
    )

    private fun credential() = UserCredential(user.id, "stored-bcrypt-hash")

    @Test
    fun `authenticates existing user with normalized email and correct password`() {
        val users = FakeUsers(user)
        val credentials = FakeCredentials(credential())
        val passwords = FakePasswords()
        val useCase = AuthenticatePersistedUserUseCase(users, credentials, passwords)

        val result = useCase.execute("  PERSON@EXAMPLE.COM  ", "correct-password")

        assertSame(user, result)
        assertEquals("person@example.com", users.searchedEmail)
        assertEquals(user.id, credentials.searchedUserId)
        assertEquals(listOf("correct-password" to "stored-bcrypt-hash"), passwords.checks)
    }

    @Test
    fun `wrong password has only generic authentication failure`() {
        val passwords = FakePasswords()
        val useCase = AuthenticatePersistedUserUseCase(
            FakeUsers(user), FakeCredentials(credential()), passwords
        )

        assertGenericFailure {
            useCase.execute("person@example.com", "wrong-password")
        }
        assertEquals(listOf("wrong-password" to "stored-bcrypt-hash"), passwords.checks)
    }

    @Test
    fun `unknown email uses dummy hash and generic authentication failure`() {
        val users = FakeUsers(null)
        val credentials = FakeCredentials(null)
        val passwords = FakePasswords()
        val useCase = AuthenticatePersistedUserUseCase(users, credentials, passwords)

        assertGenericFailure {
            useCase.execute("unknown@example.com", "correct-password")
        }
        assertEquals("unknown@example.com", users.searchedEmail)
        assertEquals(null, credentials.searchedUserId)
        assertEquals(listOf("correct-password" to "dummy-bcrypt-hash"), passwords.checks)
    }

    @Test
    fun `user without credentials uses dummy hash and generic failure`() {
        val passwords = FakePasswords()
        val credentials = FakeCredentials(null)
        val useCase = AuthenticatePersistedUserUseCase(FakeUsers(user), credentials, passwords)

        assertGenericFailure {
            useCase.execute("person@example.com", "correct-password")
        }
        assertEquals(user.id, credentials.searchedUserId)
        assertEquals(listOf("correct-password" to "dummy-bcrypt-hash"), passwords.checks)
    }

    @Test
    fun `invalid stored hash is treated as invalid credentials`() {
        val passwords = FakePasswords()
        val brokenCredential = UserCredential(user.id, "invalid-stored-hash")
        val useCase = AuthenticatePersistedUserUseCase(
            FakeUsers(user), FakeCredentials(brokenCredential), passwords
        )

        assertGenericFailure {
            useCase.execute("person@example.com", "correct-password")
        }
        assertEquals(listOf("correct-password" to "invalid-stored-hash"), passwords.checks)
    }

    @Test
    fun `malformed email has generic failure without repository lookup`() {
        val users = FakeUsers(user)
        val passwords = FakePasswords()
        val useCase = AuthenticatePersistedUserUseCase(
            users, FakeCredentials(credential()), passwords
        )

        assertGenericFailure {
            useCase.execute("not-an-email", "correct-password")
        }
        assertEquals(null, users.searchedEmail)
        assertEquals(listOf("correct-password" to "dummy-bcrypt-hash"), passwords.checks)
    }

    @Test
    fun `user repository failure remains operational error`() {
        val users = FakeUsers(user)
        val problem = IllegalStateException("database unavailable")
        users.error = problem
        val useCase = AuthenticatePersistedUserUseCase(
            users, FakeCredentials(credential()), FakePasswords()
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            useCase.execute("person@example.com", "correct-password")
        }
        assertSame(problem, thrown)
    }

    @Test
    fun `credential repository failure remains operational error`() {
        val credentials = FakeCredentials(credential())
        val problem = IllegalStateException("database unavailable")
        credentials.error = problem
        val useCase = AuthenticatePersistedUserUseCase(
            FakeUsers(user), credentials, FakePasswords()
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            useCase.execute("person@example.com", "correct-password")
        }
        assertSame(problem, thrown)
    }

    private fun assertGenericFailure(block: () -> Unit) {
        val thrown = assertThrows(InvalidCredentialsException::class.java, block)
        assertEquals("Invalid credentials", thrown.message)
    }

    private class FakePasswords : PasswordHasher {
        val checks = mutableListOf<Pair<String, String>>()

        override fun hash(rawPassword: String): String = "dummy-bcrypt-hash"

        override fun verify(rawPassword: String, encodedHash: String): Boolean {
            checks.add(rawPassword to encodedHash)
            return rawPassword == "correct-password" && encodedHash == "stored-bcrypt-hash"
        }
    }

    private class FakeUsers(private val user: User?) : UserRepository {
        var searchedEmail: String? = null
        var error: RuntimeException? = null

        override fun findById(id: UUID): User? =
            throw AssertionError("Unexpected user ID lookup")

        override fun findByEmail(email: String): User? {
            searchedEmail = email
            error?.let { throw it }
            return user?.takeIf { it.email == email }
        }

        override fun create(user: User): User =
            throw AssertionError("Unexpected user creation")
    }

    private class FakeCredentials(
        private val credential: UserCredential?
    ) : UserCredentialRepository {
        var searchedUserId: UUID? = null
        var error: RuntimeException? = null

        override fun findByUserId(userId: UUID): UserCredential? {
            searchedUserId = userId
            error?.let { throw it }
            return credential?.takeIf { it.userId == userId }
        }

        override fun create(credential: UserCredential): UserCredential =
            throw AssertionError("Unexpected credential creation")
    }
}
