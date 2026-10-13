package br.com.brunno.features.users.api

import br.com.brunno.support.UsersApiTestProfile
import br.com.brunno.support.TestUserRepository
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.equalTo
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
}
