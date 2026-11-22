package br.com.brunno.features.auth.api

import br.com.brunno.features.auth.api.dto.LoginRequest
import br.com.brunno.features.auth.application.AuthenticatePersistedUserUseCase
import br.com.brunno.features.auth.application.InvalidCredentialsException
import br.com.brunno.features.auth.application.IssuedAccessToken
import br.com.brunno.features.auth.application.LoginAttemptDecision
import br.com.brunno.features.auth.application.LoginAttemptRateLimiter
import br.com.brunno.features.auth.application.LoginPersistedUserUseCase
import br.com.brunno.features.auth.application.LoginRateLimitAdmissionService
import br.com.brunno.features.auth.application.LoginRateLimitExceededException
import br.com.brunno.features.auth.application.PasswordHasher
import br.com.brunno.features.auth.application.TokenIssuer
import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.auth.domain.UserCredentialRepository
import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRepository
import br.com.brunno.features.users.domain.UserRole
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.net.SocketAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

/** Transport mapping tests; CDI and HTTP are validated in a separate prod-profile checkpoint. */
class ProductionAuthResourceUnitTest {
    private val user =
        User(
            UUID.fromString("00000000-0000-0000-0000-000000000321"),
            "Example Admin",
            "admin@example.com",
            setOf(UserRole.ADMIN, UserRole.USER),
        )

    @Test
    fun `successful login maps UUID roles and signed bearer token without hashes`() {
        val issuer = RecordingIssuer()
        val response = resource(issuer).login(LoginRequest(" ADMIN@EXAMPLE.COM ", "valid-password"), httpRequest())

        assertEquals("signed-jwt-for-test", response.accessToken)
        assertEquals("Bearer", response.tokenType)
        assertEquals(900L, response.expiresIn)
        assertEquals(user.id.toString(), response.user.id)
        assertEquals("admin@example.com", response.user.email)
        assertEquals("Example Admin", response.user.name)
        assertEquals(setOf("USER", "ADMIN"), response.user.roles)
        assertEquals(1, issuer.calls)
    }

    @Test
    fun `invalid password does not issue token`() {
        val issuer = RecordingIssuer()
        assertThrows(InvalidCredentialsException::class.java) {
            resource(issuer).login(LoginRequest("admin@example.com", "invalid-password"), httpRequest())
        }
        assertEquals(0, issuer.calls)
    }

    @Test
    fun `unknown user does not issue token`() {
        val issuer = RecordingIssuer()
        assertThrows(InvalidCredentialsException::class.java) {
            resource(issuer).login(LoginRequest("missing@example.com", "valid-password"), httpRequest())
        }
        assertEquals(0, issuer.calls)
    }

    @Test
    fun `token issuer failure is not converted to invalid credentials`() {
        val issuer = RecordingIssuer().apply { fail = true }
        val error =
            assertThrows(IllegalStateException::class.java) {
                resource(issuer).login(LoginRequest("admin@example.com", "valid-password"), httpRequest())
            }
        assertEquals("Signing unavailable", error.message)
        assertEquals(1, issuer.calls)
    }

    @Test
    fun `login response string does not leak token or user email`() {
        val response =
            resource(RecordingIssuer())
                .login(LoginRequest("admin@example.com", "valid-password"), httpRequest())
        assertFalse(response.toString().contains(response.accessToken))
        assertFalse(response.toString().contains(response.user.email))
        assertTrue(response.toString().contains("[REDACTED]"))
    }

    @Test
    fun `admission receives normalized identity and peer IP before successful login`() {
        val limiter = RecordingLimiter()
        val issuer = RecordingIssuer()
        val response =
            resource(issuer, limiter).login(
                LoginRequest(" ADMIN@EXAMPLE.COM ", "valid-password"),
                httpRequest("192.0.2.17"),
            )
        assertEquals("signed-jwt-for-test", response.accessToken)
        assertEquals(listOf("admin@example.com" to "192.0.2.17"), limiter.calls)
        assertEquals(1, issuer.calls)
    }

    @Test
    fun `rejected admission prevents authentication and token issuance`() {
        val limiter = RecordingLimiter(LoginAttemptDecision.Rejected(43L))
        val issuer = RecordingIssuer()
        val error =
            assertThrows(LoginRateLimitExceededException::class.java) {
                resource(issuer, limiter).login(
                    LoginRequest("admin@example.com", "valid-password"),
                    httpRequest(),
                )
            }
        assertEquals(43L, error.retryAfterSeconds)
        assertEquals(1, limiter.calls.size)
        assertEquals(0, issuer.calls)
    }

    @Test
    fun `counter store failure never falls back to unthrottled authentication`() {
        val limiter =
            RecordingLimiter().apply {
                failure = IllegalStateException("counter unavailable")
            }
        val issuer = RecordingIssuer()
        val thrown =
            assertThrows(IllegalStateException::class.java) {
                resource(issuer, limiter).login(
                    LoginRequest("admin@example.com", "valid-password"),
                    httpRequest(),
                )
            }
        assertEquals("counter unavailable", thrown.message)
        assertEquals(0, issuer.calls)
    }

    @Test
    fun `missing peer address fails closed before touching the counter`() {
        val limiter = RecordingLimiter()
        val issuer = RecordingIssuer()
        assertThrows(IllegalStateException::class.java) {
            resource(issuer, limiter).login(
                LoginRequest("admin@example.com", "valid-password"),
                httpRequest(null),
            )
        }
        assertTrue(limiter.calls.isEmpty())
        assertEquals(0, issuer.calls)
    }

    private fun httpRequest(peerIp: String? = "192.0.2.17"): HttpServerRequest {
        val address = peerIp?.let { SocketAddress.inetSocketAddress(53321, it) }
        return Proxy.newProxyInstance(
            HttpServerRequest::class.java.classLoader,
            arrayOf(HttpServerRequest::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "remoteAddress" -> address
                "toString" -> "test-http-request"
                else -> error("Unexpected HTTP method: ${method.name}")
            }
        } as HttpServerRequest
    }

    private class RecordingLimiter(
        private val decision: LoginAttemptDecision = LoginAttemptDecision.Allowed,
    ) : LoginAttemptRateLimiter {
        val calls = mutableListOf<Pair<String, String>>()
        var failure: RuntimeException? = null

        override fun reserveAttempt(
            normalizedEmail: String,
            clientIp: String,
        ): LoginAttemptDecision {
            calls += normalizedEmail to clientIp
            failure?.let { throw it }
            return decision
        }
    }

    private fun resource(issuer: RecordingIssuer): ProductionAuthResource = resource(issuer, RecordingLimiter())

    private fun resource(
        issuer: RecordingIssuer,
        limiter: LoginAttemptRateLimiter,
    ): ProductionAuthResource {
        val users =
            object : UserRepository {
                override fun findByEmail(email: String): User? = user.takeIf { it.email == email }

                override fun findById(id: UUID): User? = error("Unexpected findById")

                override fun create(user: User): User = error("Unexpected create")
            }
        val credentials =
            object : UserCredentialRepository {
                override fun findByUserId(userId: UUID): UserCredential? {
                    val credential = UserCredential(user.id, "stored-hash")
                    return credential.takeIf { userId == user.id }
                }

                override fun create(credential: UserCredential): UserCredential = error("Unexpected create")
            }
        val hasher =
            object : PasswordHasher {
                override fun hash(rawPassword: String): String = "dummy-hash"

                override fun verify(
                    rawPassword: String,
                    encodedHash: String,
                ): Boolean {
                    val passwordMatches = rawPassword == "valid-password"
                    val hashMatches = encodedHash == "stored-hash"
                    return passwordMatches && hashMatches
                }
            }
        return ProductionAuthResource(
            LoginPersistedUserUseCase(
                AuthenticatePersistedUserUseCase(users, credentials, hasher),
                issuer,
            ),
            LoginRateLimitAdmissionService(limiter),
        )
    }

    private class RecordingIssuer : TokenIssuer {
        var calls = 0
        var fail = false

        override fun issue(user: User): IssuedAccessToken {
            calls++
            if (fail) throw IllegalStateException("Signing unavailable")
            return IssuedAccessToken("signed-jwt-for-test", 900L)
        }
    }
}
