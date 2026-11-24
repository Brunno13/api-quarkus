package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.InitialAdminAlreadyConfiguredException
import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import br.com.brunno.features.users.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class ReservedInitialAdminBootstrapStoreTest {
    @Test
    fun `persists credential before making admin visible`() {
        val events = mutableListOf<String>()
        val gate = FakeGate(events)
        val repository = FakeUsers(events)
        val (user, credential) = adminAndCredential()

        val created = store(gate, repository).createFirstAdmin(user, credential)

        assertSame(user, created)
        assertEquals(listOf("reserve", "check-admin", "credential", "create-user"), events)
        assertSame(credential, gate.persisted)
    }

    @Test
    fun `rejects non admin without consuming reservation`() {
        val events = mutableListOf<String>()
        val gate = FakeGate(events)
        val repository = FakeUsers(events)
        val (admin, credential) = adminAndCredential()
        val user = admin.copy(roles = setOf(UserRole.USER))

        assertThrows(IllegalArgumentException::class.java) {
            store(gate, repository).createFirstAdmin(user, credential)
        }
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `rejects extra roles without consuming reservation`() {
        val events = mutableListOf<String>()
        val (admin, credential) = adminAndCredential()
        assertThrows(IllegalArgumentException::class.java) {
            store(FakeGate(events), FakeUsers(events)).createFirstAdmin(
                admin.copy(roles = setOf(UserRole.ADMIN, UserRole.USER)),
                credential,
            )
        }
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `rejects credential with a different UUID before reservation`() {
        val events = mutableListOf<String>()
        val (admin, _) = adminAndCredential()
        val mismatched = UserCredential(UUID.randomUUID(), "hash-only")
        assertThrows(IllegalArgumentException::class.java) {
            store(FakeGate(events), FakeUsers(events)).createFirstAdmin(admin, mismatched)
        }
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `consumed reservation prevents any later write`() {
        val events = mutableListOf<String>()
        val gate = FakeGate(events, reserveFailure = InitialAdminAlreadyConfiguredException())
        val (admin, credential) = adminAndCredential()
        assertThrows(InitialAdminAlreadyConfiguredException::class.java) {
            store(gate, FakeUsers(events)).createFirstAdmin(admin, credential)
        }
        assertEquals(listOf("reserve"), events)
    }

    @Test
    fun `existing admin prevents any new credential`() {
        val events = mutableListOf<String>()
        val gate = FakeGate(events, alreadyExists = true)
        val (admin, credential) = adminAndCredential()
        assertThrows(InitialAdminAlreadyConfiguredException::class.java) {
            store(gate, FakeUsers(events)).createFirstAdmin(admin, credential)
        }
        assertEquals(listOf("reserve", "check-admin"), events)
    }

    @Test
    fun `failed admin lookup leaves reservation consumed and no writes`() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("database unavailable")
        val gate = FakeGate(events, checkFailure = failure)
        val (admin, credential) = adminAndCredential()
        val thrown =
            assertThrows(IllegalStateException::class.java) {
                store(gate, FakeUsers(events)).createFirstAdmin(admin, credential)
            }
        assertSame(failure, thrown)
        assertEquals(listOf("reserve", "check-admin"), events)
    }

    @Test
    fun `failed credential persistence does not create admin`() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("journal unavailable")
        val gate = FakeGate(events, credentialFailure = failure)
        val (admin, credential) = adminAndCredential()
        val thrown =
            assertThrows(IllegalStateException::class.java) {
                store(gate, FakeUsers(events)).createFirstAdmin(admin, credential)
            }
        assertSame(failure, thrown)
        assertEquals(listOf("reserve", "check-admin", "credential"), events)
    }

    @Test
    fun `failed user persistence propagates after credential and never retries`() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("duplicate user email")
        val gate = FakeGate(events)
        val repository = FakeUsers(events, failure)
        val (admin, credential) = adminAndCredential()
        val thrown =
            assertThrows(IllegalStateException::class.java) {
                store(gate, repository).createFirstAdmin(admin, credential)
            }
        assertSame(failure, thrown)
        assertEquals(listOf("reserve", "check-admin", "credential", "create-user"), events)
    }

    private fun store(
        gate: FakeGate,
        repository: FakeUsers,
    ) = ReservedInitialAdminBootstrapStore(gate, repository)

    private fun adminAndCredential(): Pair<User, UserCredential> {
        val id = UUID.randomUUID()
        return User(id, "First Admin", "admin@example.test", setOf(UserRole.ADMIN)) to
            UserCredential(id, "bcrypt-hash-placeholder")
    }

    private class FakeGate(
        private val events: MutableList<String>,
        private val reserveFailure: RuntimeException? = null,
        private val checkFailure: RuntimeException? = null,
        private val credentialFailure: RuntimeException? = null,
        private val alreadyExists: Boolean = false,
    ) : InitialAdminBootstrapWriteGate {
        var persisted: UserCredential? = null

        override fun reserveOnce() {
            events += "reserve"
            reserveFailure?.let { throw it }
        }

        override fun adminAlreadyExists(): Boolean {
            events += "check-admin"
            checkFailure?.let { throw it }
            return alreadyExists
        }

        override fun persistCredentialDurably(credential: UserCredential) {
            events += "credential"
            credentialFailure?.let { throw it }
            persisted = credential
        }
    }

    private class FakeUsers(
        private val events: MutableList<String>,
        private val failure: RuntimeException? = null,
    ) : UserRepository {
        override fun findById(id: UUID): User? = null

        override fun findByEmail(email: String): User? = null

        override fun create(user: User): User {
            events += "create-user"
            failure?.let { throw it }
            return user
        }
    }
}
