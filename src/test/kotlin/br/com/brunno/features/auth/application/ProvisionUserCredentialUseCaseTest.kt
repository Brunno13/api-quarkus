package br.com.brunno.features.auth.application

import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.auth.domain.UserCredentialAlreadyExistsException
import br.com.brunno.features.auth.domain.UserCredentialRepository
import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class ProvisionUserCredentialUseCaseTest {
    private val userId = UUID.randomUUID()

    @Test
    fun `provisions hashed credentials for an existing user`() {
        val credentials = FakeCredentialRepository()
        val hasher = FakePasswordHasher()
        val useCase = useCase(user(), credentials, hasher)

        useCase.execute(userId, "valid test password")

        val saved = requireNotNull(credentials.created)
        assertEquals(userId, saved.userId)
        assertEquals("test-hash-only", saved.passwordHash)
        assertEquals(listOf("valid test password"), hasher.inputs)
        assertEquals(1, credentials.createAttempts)
    }

    @Test
    fun `rejects an unknown user before accessing credentials`() {
        val credentials = FakeCredentialRepository()
        val hasher = FakePasswordHasher()
        val useCase = useCase(null, credentials, hasher)

        val error =
            assertThrows(
                UserNotFoundForCredentialProvisioningException::class.java,
            ) {
                useCase.execute(userId, "valid test password")
            }

        assertEquals("User not found", error.message)
        assertEquals(0, credentials.lookups)
        assertEquals(0, credentials.createAttempts)
        assertEquals(0, hasher.inputs.size)
    }

    @Test
    fun `rejects already provisioned credentials without hashing or writing`() {
        val credentials =
            FakeCredentialRepository(
                existing = UserCredential(userId, "existing-hash-only"),
            )
        val hasher = FakePasswordHasher()
        val useCase = useCase(user(), credentials, hasher)

        val error =
            assertThrows(
                UserCredentialAlreadyExistsException::class.java,
            ) {
                useCase.execute(userId, "valid test password")
            }

        assertEquals("Credentials already exist for this user", error.message)
        assertNull(error.cause)
        assertEquals(1, credentials.lookups)
        assertEquals(0, credentials.createAttempts)
        assertEquals(0, hasher.inputs.size)
    }

    @Test
    fun `does not write when password validation fails`() {
        val credentials = FakeCredentialRepository()
        val hasher =
            FakePasswordHasher().apply {
                hashFailure = IllegalArgumentException("Invalid password")
            }
        val useCase = useCase(user(), credentials, hasher)

        val error =
            assertThrows(InvalidInitialPasswordException::class.java) {
                useCase.execute(userId, " ")
            }

        assertEquals("Initial password is invalid", error.message)
        assertNull(error.cause)
        assertEquals(1, credentials.lookups)
        assertEquals(0, credentials.createAttempts)
    }

    @Test
    fun `preserves duplicate key exception raised during concurrent creation`() {
        val cause = IllegalStateException("simulated duplicate key")
        val duplicate = UserCredentialAlreadyExistsException(cause)
        val credentials = FakeCredentialRepository(createFailure = duplicate)
        val useCase = useCase(user(), credentials, FakePasswordHasher())

        val error =
            assertThrows(
                UserCredentialAlreadyExistsException::class.java,
            ) {
                useCase.execute(userId, "valid test password")
            }

        assertSame(duplicate, error)
        assertSame(cause, error.cause)
        assertEquals(1, credentials.createAttempts)
        assertNull(credentials.created)
    }

    private fun user(): User =
        User(
            id = userId,
            name = "Example User",
            email = "person@example.com",
        )

    private fun useCase(
        user: User?,
        credentials: FakeCredentialRepository,
        hasher: FakePasswordHasher,
    ): ProvisionUserCredentialUseCase =
        ProvisionUserCredentialUseCase(
            userRepository =
                object : UserRepository {
                    override fun findById(id: UUID): User? = user?.takeIf { it.id == id }

                    override fun findByEmail(email: String): User? = throw AssertionError("Unexpected findByEmail call")

                    override fun create(user: User): User = throw AssertionError("Unexpected user creation")
                },
            credentialRepository = credentials,
            passwordHasher = hasher,
        )

    private class FakeCredentialRepository(
        private val existing: UserCredential? = null,
        private val createFailure: RuntimeException? = null,
    ) : UserCredentialRepository {
        var lookups = 0
        var createAttempts = 0
        var created: UserCredential? = null

        override fun findByUserId(userId: UUID): UserCredential? {
            lookups++
            return existing?.takeIf { it.userId == userId }
        }

        override fun create(credential: UserCredential): UserCredential {
            createAttempts++
            createFailure?.let { throw it }
            created = credential
            return credential
        }
    }

    private class FakePasswordHasher : PasswordHasher {
        val inputs = mutableListOf<String>()
        var hashFailure: RuntimeException? = null

        override fun hash(rawPassword: String): String {
            inputs.add(rawPassword)
            hashFailure?.let { throw it }
            return "test-hash-only"
        }

        override fun verify(
            rawPassword: String,
            encodedHash: String,
        ): Boolean = throw AssertionError("Unexpected password verification")
    }
}
