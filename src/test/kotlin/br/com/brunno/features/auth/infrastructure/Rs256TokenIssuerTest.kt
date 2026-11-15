package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRole
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

class Rs256TokenIssuerTest {

    private val user = User(
        id = UUID.fromString("00000000-0000-0000-0000-000000000123"),
        name = "Example User",
        email = "person@example.com",
        roles = setOf(UserRole.USER, UserRole.ADMIN)
    )
    private val clock = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC)
    private val json = ObjectMapper()

    @Test
    fun `issues RS256 token with UUID subject persisted roles issuer and exact lifetime`() {
        val issued = issuer().issue(user)
        val parts = issued.accessToken.split('.')
        assertEquals(3, parts.size)

        val header = json.readTree(decode(parts[0]))
        val payload = json.readTree(decode(parts[1]))

        assertEquals("RS256", header["alg"].asText())
        assertEquals("api-quarkus", payload["iss"].asText())
        assertEquals(user.id.toString(), payload["sub"].asText())
        assertEquals(user.email, payload["upn"].asText())
        assertEquals(setOf("USER", "ADMIN"), payload["groups"].map { it.asText() }.toSet())
        assertEquals(clock.instant().epochSecond, payload["iat"].asLong())
        assertEquals(clock.instant().epochSecond + 900, payload["exp"].asLong())
        assertEquals(900L, issued.expiresInSeconds)
        assertTrue(verify(issued.accessToken, signingKeys.public))
    }

    @Test
    fun `token signature cannot be verified with another RSA public key`() {
        val token = issuer().issue(user).accessToken
        val otherKeys = generateKeys()

        assertTrue(verify(token, signingKeys.public))
        assertFalse(verify(token, otherKeys.public))
    }

    @Test
    fun `tampering with signed JWT payload invalidates signature`() {
        val token = issuer().issue(user).accessToken
        val parts = token.split('.')
        val alteredPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"attacker"}""".toByteArray(StandardCharsets.UTF_8))
        val tampered = "${parts[0]}.$alteredPayload.${parts[2]}"

        assertFalse(verify(tampered, signingKeys.public))
    }

    @Test
    fun `rejects blank issuer and invalid lifetimes`() {
        assertThrows(IllegalArgumentException::class.java) {
            issuer(issuer = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            issuer(ttlSeconds = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            issuer(ttlSeconds = 3601)
        }
    }

    @Test
    fun `rejects undersized RSA key`() {
        val undersized = generateKeys(1024).private as RSAPrivateKey
        assertThrows(IllegalArgumentException::class.java) {
            Rs256TokenIssuer(undersized, "api-quarkus", 900, clock)
        }
    }

    @Test
    fun `issued token string representation does not expose the bearer token`() {
        val token = issuer().issue(user)
        assertFalse(token.toString().contains(token.accessToken))
        assertTrue(token.toString().contains("[REDACTED]"))
    }

    private fun issuer(
        issuer: String = "api-quarkus",
        ttlSeconds: Long = 900
    ): Rs256TokenIssuer = Rs256TokenIssuer(
        signingKeys.private as RSAPrivateKey,
        issuer,
        ttlSeconds,
        clock
    )

    private fun decode(segment: String): ByteArray =
        Base64.getUrlDecoder().decode(segment)

    private fun verify(token: String, publicKey: PublicKey): Boolean {
        val parts = token.split('.')
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(publicKey)
        signature.update("${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.US_ASCII))
        return signature.verify(decode(parts[2]))
    }

    companion object {
        private val signingKeys: KeyPair = generateKeys()

        private fun generateKeys(bits: Int = 2048): KeyPair =
            KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()
    }
}
