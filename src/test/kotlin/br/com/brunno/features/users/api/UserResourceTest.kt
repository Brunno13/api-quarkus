package br.com.brunno.features.users.api

import br.com.brunno.support.UsersApiTestProfile
import br.com.brunno.support.TestUserRepository
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import java.util.UUID

@QuarkusTest
@TestProfile(UsersApiTestProfile::class)
class UserResourceTest {

    @Test
    fun `unauthenticated request returns HTTP 401`() {
        val id = UUID.randomUUID()

        given()
            .`when`()
            .get("/users/$id")
            .then()
            .statusCode(401)
    }

    @Test
    @TestSecurity(
        user = "regular-user",
        roles = ["USER"]
    )
    fun `user without ADMIN role returns HTTP 403`() {
        val id = UUID.randomUUID()

        given()
            .`when`()
            .get("/users/$id")
            .then()
            .statusCode(403)
    }

    @Test
    @TestSecurity(
        user = "admin",
        roles = ["ADMIN"]
    )
    fun `admin retrieves existing user with HTTP 200`() {
        val user = TestUserRepository.EXISTING_USER

        given()
            .`when`()
            .get("/users/${user.id}")
            .then()
            .statusCode(200)
            .body(
                "id", equalTo(user.id.toString()),
                "name", equalTo(user.name),
                "email", equalTo(user.email)
            )
    }

    @Test
    @TestSecurity(
        user = "admin",
        roles = ["ADMIN"]
    )
    fun `admin receives HTTP 404 when user does not exist`() {
        val unknownId = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
        )

        given()
            .`when`()
            .get("/users/$unknownId")
            .then()
            .statusCode(404)
    }

    @Test
    fun `unauthenticated create returns HTTP 401`() {
        given()
            .contentType("application/json")
            .body(
                """
                {
                    "name": "Example User",
                    "email": "person@example.com"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/users")
            .then()
            .statusCode(401)
    }

    @Test
    @TestSecurity(
        user = "regular-user",
        roles = ["USER"]
    )
    fun `user without ADMIN role cannot create users`() {
        given()
            .contentType("application/json")
            .body(
                """
                {
                    "name": "Example User",
                    "email": "person@example.com"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/users")
            .then()
            .statusCode(403)
    }

    @Test
    @TestSecurity(
        user = "admin",
        roles = ["ADMIN"]
    )
    fun `admin creates user with HTTP 201`() {
        given()
            .contentType("application/json")
            .body(
                """
                {
                    "name": "  Example User  ",
                    "email": "  PERSON@EXAMPLE.COM  "
                }
                """.trimIndent()
            )
            .`when`()
            .post("/users")
            .then()
            .statusCode(201)
            .body(
                "id", notNullValue(),
                "name", equalTo("Example User"),
                "email", equalTo("person@example.com")
            )
    }

    @Test
    @TestSecurity(
        user = "admin",
        roles = ["ADMIN"]
    )
    fun `blank user name returns HTTP 400`() {
        given()
            .contentType("application/json")
            .body(
                """
                {
                    "name": "   ",
                    "email": "person@example.com"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/users")
            .then()
            .statusCode(400)
    }

    @Test
    @TestSecurity(
        user = "admin",
        roles = ["ADMIN"]
    )
    fun `blank user email returns HTTP 400`() {
        given()
            .contentType("application/json")
            .body(
                """
                {
                    "name": "Example User",
                    "email": "   "
                }
                """.trimIndent()
            )
            .`when`()
            .post("/users")
            .then()
            .statusCode(400)
    }

    @Test
    @TestSecurity(
        user = "admin",
        roles = ["ADMIN"]
    )
    fun `malformed user email returns HTTP 400`() {
        given()
            .contentType("application/json")
            .body(
                """
                {
                    "name": "Example User",
                    "email": "invalid-email"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/users")
            .then()
            .statusCode(400)
            .body(
                "title", equalTo("Bad Request"),
                "status", equalTo(400),
                "detail", equalTo("User email has an invalid format")
            )
    }

    @Test
    @TestSecurity(
        user = "admin",
        roles = ["ADMIN"]
    )
    fun `duplicate user email returns HTTP 409`() {
        given()
            .contentType("application/json")
            .body(
                """
                {
                    "name": "Another User",
                    "email": "existing@example.com"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/users")
            .then()
            .statusCode(409)
            .body(
                "title", equalTo("Conflict"),
                "status", equalTo(409),
                "detail", equalTo("User email already exists")
            )
    }
}
