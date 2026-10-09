package br.com.brunno.features.auth.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test

@QuarkusTest
class AuthResourceTest {

    @Test
    fun `valid credentials return HTTP 200`() {
        given()
            .contentType("application/json")
            .body(
                """
                {
                    "email": "admin@local.dev",
                    "password": "admin123"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/auth/login")
            .then()
            .statusCode(200)
            .body(
                "accessToken", equalTo("mock-access-token"),
                "tokenType", equalTo("Bearer"),
                "expiresIn", equalTo(900),
                "user.email", equalTo("admin@local.dev"),
                "user.roles", hasItem("ADMIN")
            )
    }

    @Test
    fun `invalid credentials return HTTP 401`() {
        given()
            .contentType("application/json")
            .body(
                """
                {
                    "email": "wrong@test.com",
                    "password": "wrong"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/auth/login")
            .then()
            .statusCode(401)
            .body(
                "title", equalTo("Unauthorized"),
                "status", equalTo(401),
                "detail", equalTo("Invalid credentials")
            )
    }

    @Test
    fun `invalid email returns HTTP 400`() {
        given()
            .contentType("application/json")
            .body(
                """
                {
                    "email": "invalid",
                    "password": "admin123"
                }
                """.trimIndent()
            )
            .`when`()
            .post("/auth/login")
            .then()
            .statusCode(400)
            .body(
                "title", equalTo("Constraint Violation"),
                "status", equalTo(400)
            )
    }

    @Test
    fun `blank password returns HTTP 400`() {
        given()
            .contentType("application/json")
            .body(
                """
                {
                    "email": "admin@local.dev",
                    "password": ""
                }
                """.trimIndent()
            )
            .`when`()
            .post("/auth/login")
            .then()
            .statusCode(400)
    }
}
