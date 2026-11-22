package br.com.brunno.features.auth.application

import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.users.application.InvalidUserEmailFormatException
import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BootstrapFirstAdminUseCaseTest {
    private val defaultPassword = "a test-only secret"

    @Test
    fun `creates an ADMIN with normalized email and hashed credential`() {
        val store = FakeStore()
        val hasher = FakeHasher()

        val created =
            useCase(store, hasher).execute(
                command(name = "  First Admin  ", email = "  ADMIN@Example.Test  "),
            )

        assertSame(created, store.user)
        assertEquals("First Admin", created.name)
        assertEquals("admin@example.test", created.email)
        assertEquals(setOf(UserRole.ADMIN), created.roles)
        assertEquals(created.id, store.credential?.userId)
        assertEquals("test-hash-not-a-secret", store.credential?.passwordHash)
        assertEquals(listOf(defaultPassword), hasher.inputs)
        assertEquals(1, store.calls)
    }

    @Test
    fun `rejects a blank name without hashing or writing`() {
        val store = FakeStore()
        val hasher = FakeHasher()
        assertThrows(IllegalArgumentException::class.java) {
            useCase(store, hasher).execute(command(name = "  "))
        }
        assertEquals(0, store.calls)
        assertEquals(0, hasher.inputs.size)
    }

    @Test
    fun `rejects a blank email without hashing or writing`() {
        val store = FakeStore()
        val hasher = FakeHasher()
        assertThrows(IllegalArgumentException::class.java) {
            useCase(store, hasher).execute(command(email = "  "))
        }
        assertEquals(0, store.calls)
        assertEquals(0, hasher.inputs.size)
    }

    @Test
    fun `rejects a malformed email without hashing or writing`() {
        val store = FakeStore()
        val hasher = FakeHasher()
        assertThrows(InvalidUserEmailFormatException::class.java) {
            useCase(store, hasher).execute(command(email = "not-an-email"))
        }
        assertEquals(0, store.calls)
        assertEquals(0, hasher.inputs.size)
    }

    @Test
    fun `maps invalid password to existing provisioning error without writing`() {
        val store = FakeStore()
        val hasher = FakeHasher(failure = IllegalArgumentException("raw password rejected"))
        val error =
            assertThrows(InvalidInitialPasswordException::class.java) {
                useCase(store, hasher).execute(command())
            }
        assertFalse(error.message.orEmpty().contains(defaultPassword))
        assertEquals(0, store.calls)
    }

    @Test
    fun `does not swallow a bootstrap already consumed error`() {
        val store = FakeStore(failure = InitialAdminAlreadyConfiguredException())
        val hasher = FakeHasher()
        assertThrows(InitialAdminAlreadyConfiguredException::class.java) {
            useCase(store, hasher).execute(command())
        }
        assertEquals(1, store.calls)
    }

    @Test
    fun `propagates infrastructure failures without returning success`() {
        val failure = IllegalStateException("database unavailable")
        val store = FakeStore(failure = failure)
        val actual =
            assertThrows(IllegalStateException::class.java) {
                useCase(store, FakeHasher()).execute(command())
            }
        assertSame(failure, actual)
        assertNull(store.user)
    }

    @Test
    fun `propagates operational hashing failures without persisting`() {
        val failure = IllegalStateException("BCrypt provider unavailable")
        val store = FakeStore()
        val actual =
            assertThrows(IllegalStateException::class.java) {
                useCase(store, FakeHasher(failure)).execute(command())
            }
        assertSame(failure, actual)
        assertEquals(0, store.calls)
    }

    @Test
    fun `command string representation redacts sensitive values`() {
        val command = command(name = "Confidential Name", email = "hidden@example.test")
        val description = command.toString()
        assertFalse(description.contains(defaultPassword))
        assertFalse(description.contains("Confidential Name"))
        assertFalse(description.contains("hidden@example.test"))
        assertNotNull(description)
        assertEquals(3, Regex(Regex.escape("[REDACTED]")).findAll(description).count())
    }

    private fun command(
        name: String = "First Admin",
        email: String = "admin@example.test",
    ) = BootstrapFirstAdminCommand(name, email, defaultPassword)

    private fun useCase(
        store: FakeStore,
        hasher: FakeHasher,
    ) = BootstrapFirstAdminUseCase(store, hasher)

    private class FakeStore(
        private val failure: RuntimeException? = null,
    ) : InitialAdminBootstrapStore {
        var calls: Int = 0
        var user: User? = null
        var credential: UserCredential? = null

        override fun createFirstAdmin(
            user: User,
            credential: UserCredential,
        ): User {
            calls++
            failure?.let { throw it }
            this.user = user
            this.credential = credential
            return user
        }
    }

    private class FakeHasher(
        private val failure: RuntimeException? = null,
    ) : PasswordHasher {
        val inputs = mutableListOf<String>()

        override fun hash(rawPassword: String): String {
            inputs.add(rawPassword)
            failure?.let { throw it }
            return "test-hash-not-a-secret"
        }

        override fun verify(
            rawPassword: String,
            encodedHash: String,
        ): Boolean = throw UnsupportedOperationException("not needed in this use case")
    }
}
