package br.com.brunno.features.auth.api

import br.com.brunno.support.CredentialProvisioningApiTestProfile
import br.com.brunno.support.TestUserCredentialRepository
import br.com.brunno.support.TestUserRepository
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import io.smallrye.jwt.build.Jwt
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
@TestProfile(CredentialProvisioningApiTestProfile::class)
class UserCredentialResourceJwtTest {
    @Inject
    lateinit var credentials: TestUserCredentialRepository

    private val userId = TestUserRepository.EXISTING_USER.id
    private val path = "/users/$userId/credentials"
    private val requestBody = """{"password":"Example-passphrase-2026"}"""

    @BeforeEach
    fun resetCredentials() {
        credentials.reset()
    }

    @Test
    fun `signed RS256 ADMIN JWT provisions credentials with HTTP 204`() {
        val token =
            Jwt
                .issuer("api-quarkus")
                .subject(UUID.randomUUID().toString())
                .upn("jwt-admin")
                .groups(setOf("ADMIN"))
                .sign()

        given()
            .auth()
            .oauth2(token)
            .contentType("application/json")
            .body(requestBody)
            .`when`()
            .put(path)
            .then()
            .statusCode(204)

        assertNotNull(credentials.findByUserId(userId))
    }

    @Test
    fun `signed RS256 USER JWT cannot provision credentials`() {
        val token =
            Jwt
                .issuer("api-quarkus")
                .subject(UUID.randomUUID().toString())
                .upn("jwt-user")
                .groups(setOf("USER"))
                .sign()

        given()
            .auth()
            .oauth2(token)
            .contentType("application/json")
            .body(requestBody)
            .`when`()
            .put(path)
            .then()
            .statusCode(403)

        assertNull(credentials.findByUserId(userId))
    }

    @Test
    fun `RS256 JWT with unexpected issuer cannot provision credentials`() {
        val token =
            Jwt
                .issuer("unexpected-issuer")
                .subject(UUID.randomUUID().toString())
                .upn("jwt-admin")
                .groups(setOf("ADMIN"))
                .sign()

        given()
            .auth()
            .oauth2(token)
            .contentType("application/json")
            .body(requestBody)
            .`when`()
            .put(path)
            .then()
            .statusCode(401)

        assertNull(credentials.findByUserId(userId))
    }

    @Test
    fun `RS256 JWT with tampered signature cannot provision credentials`() {
        val token =
            Jwt
                .issuer("api-quarkus")
                .subject(UUID.randomUUID().toString())
                .upn("jwt-admin")
                .groups(setOf("ADMIN"))
                .sign()

        val parts = token.split('.')
        val replacement = if (parts[2][0] == 'A') 'B' else 'A'
        val tamperedSignature = replacement.toString() + parts[2].drop(1)
        val tamperedToken = "${parts[0]}.${parts[1]}.$tamperedSignature"

        given()
            .auth()
            .oauth2(tamperedToken)
            .contentType("application/json")
            .body(requestBody)
            .`when`()
            .put(path)
            .then()
            .statusCode(401)

        assertNull(credentials.findByUserId(userId))
    }

    @Test
    fun `expired RS256 ADMIN JWT cannot provision credentials`() {
        val now = Instant.now().epochSecond
        val token =
            Jwt
                .issuer("api-quarkus")
                .subject(UUID.randomUUID().toString())
                .upn("jwt-admin")
                .groups(setOf("ADMIN"))
                .claim("iat", now - 3600)
                .claim("exp", now - 300)
                .sign()

        given()
            .auth()
            .oauth2(token)
            .contentType("application/json")
            .body(requestBody)
            .`when`()
            .put(path)
            .then()
            .statusCode(401)

        assertNull(credentials.findByUserId(userId))
    }
}
