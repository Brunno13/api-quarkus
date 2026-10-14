package br.com.brunno.features.users.api

import br.com.brunno.support.TestUserRepository
import br.com.brunno.support.UsersApiTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import io.smallrye.jwt.build.Jwt
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import java.util.UUID
import java.time.Instant

@QuarkusTest
@TestProfile(UsersApiTestProfile::class)
class UserResourceJwtTest {

    @Test
    fun `valid RS256 ADMIN token accesses protected user endpoint`() {
        val token = Jwt.issuer("api-quarkus")
            .subject(UUID.randomUUID().toString())
            .upn("jwt-admin")
            .groups(setOf("ADMIN"))
            .sign()

        val user = TestUserRepository.EXISTING_USER

        given()
            .auth()
            .oauth2(token)
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
    fun `valid RS256 USER token cannot access ADMIN endpoint`() {
        val token = Jwt.issuer("api-quarkus")
            .subject(UUID.randomUUID().toString())
            .upn("jwt-user")
            .groups(setOf("USER"))
            .sign()

        val user = TestUserRepository.EXISTING_USER

        given()
            .auth()
            .oauth2(token)
            .`when`()
            .get("/users/${user.id}")
            .then()
            .statusCode(403)
    }

    @Test
    fun `JWT with unexpected issuer returns HTTP 401`() {
        val token = Jwt.issuer("unexpected-issuer")
            .subject(UUID.randomUUID().toString())
            .upn("jwt-admin")
            .groups(setOf("ADMIN"))
            .sign()

        val user = TestUserRepository.EXISTING_USER

        given()
            .auth()
            .oauth2(token)
            .`when`()
            .get("/users/${user.id}")
            .then()
            .statusCode(401)
    }

    @Test
    fun `JWT with tampered signature returns HTTP 401`() {
        val token = Jwt.issuer("api-quarkus")
            .subject(UUID.randomUUID().toString())
            .upn("jwt-admin")
            .groups(setOf("ADMIN"))
            .sign()

        val parts = token.split('.')

        val replacement = if (parts[2][0] == 'A') 'B' else 'A'
        val tamperedSignature =
            replacement.toString() + parts[2].drop(1)

        val tamperedToken =
            "${parts[0]}.${parts[1]}.$tamperedSignature"

        val user = TestUserRepository.EXISTING_USER

        given()
            .auth()
            .oauth2(tamperedToken)
            .`when`()
            .get("/users/${user.id}")
            .then()
            .statusCode(401)
    }

    @Test
    fun `expired RS256 ADMIN token returns HTTP 401`() {
        val now = Instant.now().epochSecond

        val token = Jwt.issuer("api-quarkus")
            .subject(UUID.randomUUID().toString())
            .upn("jwt-admin")
            .groups(setOf("ADMIN"))
            .claim("iat", now - 3600)
            .claim("exp", now - 300)
            .sign()

        val user = TestUserRepository.EXISTING_USER

        given()
            .auth()
            .oauth2(token)
            .`when`()
            .get("/users/${user.id}")
            .then()
            .statusCode(401)
    }
}
