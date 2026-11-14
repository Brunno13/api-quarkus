package br.com.brunno.features.auth.api

import br.com.brunno.support.CredentialProvisioningApiTestProfile
import br.com.brunno.support.TestUserCredentialRepository
import br.com.brunno.support.TestUserRepository
import io.quarkus.elytron.security.common.BcryptUtil
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
@TestProfile(CredentialProvisioningApiTestProfile::class)
class UserCredentialResourceTest {

    @Inject
    lateinit var credentials: TestUserCredentialRepository

    private val existingId: UUID = TestUserRepository.EXISTING_USER.id
    private val path: String = "/users/$existingId/credentials"

    @BeforeEach
    fun resetCredentials() {
        credentials.reset()
    }

    @Test
    fun `unauthenticated provisioning returns HTTP 401`() {
        given()
            .contentType("application/json")
            .body("""{"password":"Example-passphrase-2026"}""")
            .`when`()
            .put(path)
            .then()
            .statusCode(401)
    }

    @Test
    @TestSecurity(user = "regular-user", roles = ["USER"])
    fun `non admin provisioning returns HTTP 403`() {
        given()
            .contentType("application/json")
            .body("""{"password":"Example-passphrase-2026"}""")
            .`when`()
            .put(path)
            .then()
            .statusCode(403)
    }

    @Test
    @TestSecurity(user = "admin", roles = ["ADMIN"])
    fun `admin provisions BCrypt credentials and receives HTTP 204`() {
        val password = "Example-passphrase-2026"

        given()
            .contentType("application/json")
            .body("""{"password":"$password"}""")
            .`when`()
            .put(path)
            .then()
            .statusCode(204)
            .body(equalTo(""))

        val saved = credentials.findByUserId(existingId)
        assertNotNull(saved)
        val hash = requireNotNull(saved).passwordHash
        assertNotEquals(password, hash)
        assertTrue(BcryptUtil.matches(password, hash))
    }

    @Test
    @TestSecurity(user = "admin", roles = ["ADMIN"])
    fun `unknown user returns HTTP 404`() {
        val unknownId = UUID.fromString("00000000-0000-0000-0000-000000000002")

        given()
            .contentType("application/json")
            .body("""{"password":"Example-passphrase-2026"}""")
            .`when`()
            .put("/users/$unknownId/credentials")
            .then()
            .statusCode(404)
            .body(
                "title", equalTo("Not Found"),
                "status", equalTo(404),
                "detail", equalTo("User not found")
            )
    }

    @Test
    @TestSecurity(user = "admin", roles = ["ADMIN"])
    fun `repeated provisioning returns HTTP 409 and preserves original hash`() {
        given()
            .contentType("application/json")
            .body("""{"password":"Example-passphrase-2026"}""")
            .`when`()
            .put(path)
            .then()
            .statusCode(204)

        val originalHash = requireNotNull(credentials.findByUserId(existingId)).passwordHash

        given()
            .contentType("application/json")
            .body("""{"password":"Different-passphrase-2026"}""")
            .`when`()
            .put(path)
            .then()
            .statusCode(409)
            .body(
                "title", equalTo("Conflict"),
                "status", equalTo(409),
                "detail", equalTo("Credentials already exist for this user")
            )

        assertTrue(credentials.findByUserId(existingId)?.passwordHash == originalHash)
    }

    @Test
    @TestSecurity(user = "admin", roles = ["ADMIN"])
    fun `blank password returns HTTP 400`() {
        given()
            .contentType("application/json")
            .body("""{"password":"   "}""")
            .`when`()
            .put(path)
            .then()
            .statusCode(400)
            .body(
                "title", equalTo("Bad Request"),
                "status", equalTo(400),
                "detail", equalTo("Initial password is invalid")
            )
    }

    @Test
    @TestSecurity(user = "admin", roles = ["ADMIN"])
    fun `oversized UTF8 password returns HTTP 400`() {
        val password = "á".repeat(37)

        given()
            .contentType("application/json")
            .body("""{"password":"$password"}""")
            .`when`()
            .put(path)
            .then()
            .statusCode(400)
            .body(
                "status", equalTo(400),
                "detail", equalTo("Initial password is invalid")
            )
    }

    @Test
    @TestSecurity(user = "admin", roles = ["ADMIN"])
    fun `missing password returns HTTP 400`() {
        given()
            .contentType("application/json")
            .body("{}")
            .`when`()
            .put(path)
            .then()
            .statusCode(400)
            .body(
                "status", equalTo(400),
                "detail", equalTo("Initial password is invalid")
            )
    }
}
