package br.com.brunno.features.users.api

import br.com.brunno.features.users.application.GetUserByIdUseCase
import br.com.brunno.support.UsersApiTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
@TestProfile(UsersApiTestProfile::class)
class UsersApiWiringTest {

    @Inject
    lateinit var getUserByIdUseCase: GetUserByIdUseCase

    @Test
    fun `resolves user use case without MongoDB`() {
        val result = getUserByIdUseCase.execute(
            UUID.randomUUID()
        )

        assertNull(result)
    }
}
