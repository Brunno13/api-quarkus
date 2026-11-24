package br.com.brunno.features.auth.api

import br.com.brunno.features.auth.application.TokenIssuer
import io.quarkus.arc.Arc
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
class ProductionTokenIssuerProfileTest {
    @Test
    fun `production token issuer is not available in test profile`() {
        assertTrue(!Arc.container().instance(TokenIssuer::class.java).isAvailable)
    }
}
