package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.TokenIssuer
import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Produces
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.nio.file.Path
import java.util.Optional

/** Only production builds can obtain a TokenIssuer backed by external RSA keys. */
@Dependent
@IfBuildProfile("prod")
class ProductionTokenIssuerProducer {
    @Produces
    @ApplicationScoped
    @Startup
    fun tokenIssuer(
        @ConfigProperty(name = "app.auth.jwt.private-key-path") privateKeyLocation: Optional<String>,
        @ConfigProperty(name = "mp.jwt.verify.publickey.location") verificationKeyLocation: String,
        @ConfigProperty(name = "mp.jwt.verify.issuer") issuer: String,
        @ConfigProperty(name = "app.auth.jwt.access-token-ttl-seconds", defaultValue = "900") ttl: Long,
    ): TokenIssuer {
        val privateLocation =
            privateKeyLocation
                .filter { it.isNotBlank() }
                .orElseThrow { IllegalStateException("JWT signing private key is not configured") }
        return ExternalRsaJwtIssuerFactory().create(
            externalFilePath(privateLocation),
            externalFilePath(verificationKeyLocation),
            issuer,
            ttl,
        )
    }

    private fun externalFilePath(location: String): Path {
        require(location.isNotBlank()) { "JWT key location must not be blank" }
        val path =
            if (location.startsWith("file:")) {
                Path.of(URI.create(location))
            } else {
                Path.of(location)
            }
        require(path.isAbsolute) { "JWT keys must be loaded from absolute external file paths" }
        return path
    }
}
