package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.IssuedAccessToken
import br.com.brunno.features.auth.application.TokenIssuer
import br.com.brunno.features.users.domain.User
import io.smallrye.jwt.algorithm.SignatureAlgorithm
import io.smallrye.jwt.build.Jwt
import java.security.interfaces.RSAPrivateKey
import java.time.Clock

/**
 * Signs short-lived access tokens with an RSA private key supplied by the caller.
 *
 * This is deliberately not a CDI bean: production key loading and HTTP exposure
 * must be configured and verified separately. No key material is embedded here.
 */
class Rs256TokenIssuer(
    private val signingKey: RSAPrivateKey,
    private val issuer: String,
    private val accessTokenTtlSeconds: Long = 900,
    private val clock: Clock = Clock.systemUTC()
) : TokenIssuer {

    init {
        require(signingKey.modulus.bitLength() >= 2048) {
            "RSA signing key must be at least 2048 bits"
        }
        require(issuer.isNotBlank()) { "Token issuer must not be blank" }
        require(accessTokenTtlSeconds in 1..3600) {
            "Access token lifetime must be between 1 and 3600 seconds"
        }
    }

    override fun issue(user: User): IssuedAccessToken {
        val issuedAt = clock.instant().epochSecond
        val expiresAt = Math.addExact(issuedAt, accessTokenTtlSeconds)

        val signedToken = Jwt.claims()
            .issuer(issuer)
            .subject(user.id.toString())
            .upn(user.email)
            .groups(user.roles.map { it.name }.toSet())
            .claim("iat", issuedAt)
            .claim("exp", expiresAt)
            .jws()
            .algorithm(SignatureAlgorithm.RS256)
            .sign(signingKey)

        return IssuedAccessToken(signedToken, accessTokenTtlSeconds)
    }
}
