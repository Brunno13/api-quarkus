package br.com.brunno.features.auth.application

import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.auth.domain.UserCredentialRepository
import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import br.com.brunno.features.users.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class LoginPersistedUserUseCaseTest {

    private val user = User(
        id = UUID.fromString("00000000-0000-0000-0000-000000000123"),
        name = "Example User",
        email = "person@example.com",
        roles = setOf(UserRole.USER, UserRole.ADMIN)
    )

    @Test
    fun `valid credentials issue a token for the persisted user`() {
        val issuer = RecordingIssuer()
        val useCase = useCase(user, credential(), issuer)

        val result = useCase.execute("  PERSON@EXAMPLE.COM  ", "correct-password")

        assertSame(user, result.user)
        assertSame(user, issuer.issuedFor)
        assertEquals(1, issuer.calls)
        assertEquals("signed-test-token", result.token.accessToken)
        assertEquals(900L, result.token.expiresInSeconds)
        assertEquals(user.id, result.user.id)
        assertEquals(setOf(UserRole.USER, UserRole.ADMIN), result.user.roles)
    }

    @Test
    fun `wrong password never invokes token issuer`() {
        val issuer = RecordingIssuer()
        val useCase = useCase(user, credential(), issuer)

        assertThrows(InvalidCredentialsException::class.java) {
            useCase.execute("person@example.com", "wrong-password")
        }

        assertEquals(0, issuer.calls)
    }

    @Test
    fun `unknown user never invokes token issuer`() {
        val issuer = RecordingIssuer()
        val useCase = useCase(null, null, issuer)

        assertThrows(InvalidCredentialsException::class.java) {
            useCase.execute("missing@example.com", "correct-password")
        }

        assertEquals(0, issuer.calls)
    }

    @Test
    fun `user without credentials never invokes token issuer`() {
        val issuer = RecordingIssuer()
        val useCase = useCase(user, null, issuer)

        assertThrows(InvalidCredentialsException::class.java) {
            useCase.execute("person@example.com", "correct-password")
        }

        assertEquals(0, issuer.calls)
    }

    @Test
    fun `malformed email never invokes token issuer`() {
        val issuer = RecordingIssuer()
        val useCase = useCase(user, credential(), issuer)

        assertThrows(InvalidCredentialsException::class.java) {
            useCase.execute("not-an-email", "correct-password")
        }

        assertEquals(0, issuer.calls)
    }

    @Test
    fun `token issuer failure remains an operational error`() {
        val issuer = RecordingIssuer()
        val failure = IllegalStateException("signing key unavailable")
        issuer.error = failure
        val useCase = useCase(user, credential(), issuer)

        val thrown = assertThrows(IllegalStateException::class.java) {
            useCase.execute("person@example.com", "correct-password")
        }

        assertSame(failure, thrown)
        assertEquals(1, issuer.calls)
    }

    @Test
    fun `issued token rejects blank value or nonpositive lifetime`() {
        assertThrows(IllegalArgumentException::class.java) {
            IssuedAccessToken("  ", 900L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IssuedAccessToken("signed-test-token", 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IssuedAccessToken("signed-test-token", -1L)
        }
    }

    @Test
    fun `application results do not expose bearer token or user email in strings`() {
        val token = IssuedAccessToken("private-signed-token", 900L)
        val session = AuthenticatedSession(user, token)

        assertFalse(token.toString().contains(token.accessToken))
        assertTrue(token.toString().contains("[REDACTED]"))
        assertFalse(session.toString().contains(token.accessToken))
        assertFalse(session.toString().contains(user.email))
        assertTrue(session.toString().contains("[REDACTED]"))
    }

    private fun credential(): UserCredential =
        UserCredential(user.id, "stored-bcrypt-hash")

    private fun useCase(
        persistedUser: User?,
        credential: UserCredential?,
        issuer: TokenIssuer
    ): LoginPersistedUserUseCase {
        val authenticate = AuthenticatePersistedUserUseCase(
            FakeUsers(persistedUser),
            FakeCredentials(credential),
            FakePasswords()
        )
        return LoginPersistedUserUseCase(authenticate, issuer)
    }

    private class FakeUsers(private val persistedUser: User?) : UserRepository {
        override fun findByEmail(email: String): User? =
            persistedUser?.takeIf { it.email == email }

        override fun findById(id: UUID): User? =
            throw AssertionError("Unexpected lookup by ID")

        override fun create(user: User): User =
            throw AssertionError("Unexpected user creation")
    }

    private class FakeCredentials(
        private val credential: UserCredential?
    ) : UserCredentialRepository {
        override fun findByUserId(userId: UUID): UserCredential? =
            credential?.takeIf { it.userId == userId }

        override fun create(credential: UserCredential): UserCredential =
            throw AssertionError("Unexpected credential creation")
    }

    private class FakePasswords : PasswordHasher {
        override fun hash(rawPassword: String): String = "dummy-bcrypt-hash"

        override fun verify(rawPassword: String, encodedHash: String): Boolean =
            rawPassword == "correct-password" && encodedHash == "stored-bcrypt-hash"
    }

    private class RecordingIssuer : TokenIssuer {
        var calls: Int = 0
        var issuedFor: User? = null
        var error: RuntimeException? = null

        override fun issue(user: User): IssuedAccessToken {
            calls++
            issuedFor = user
            error?.let { throw it }
            return IssuedAccessToken("signed-test-token", 900L)
        }
    }
}
