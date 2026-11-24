package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.TokenIssuer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.SignatureException
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.util.Base64

/** Builds an issuer only after validating the external signing and verification keys. */
class ExternalRsaJwtIssuerFactory(
    private val privateKeyLoader: Pkcs8RsaPrivateKeyLoader = Pkcs8RsaPrivateKeyLoader(),
) {
    fun create(
        privateKeyPath: Path,
        verificationPublicKeyPath: Path,
        issuer: String,
        ttlSeconds: Long = 900,
        clock: Clock = Clock.systemUTC(),
    ): TokenIssuer {
        val privateKey = privateKeyLoader.load(privateKeyPath)
        val publicKey = loadPublicKey(verificationPublicKeyPath)

        // A matching RSA modulus alone is insufficient to validate a usable key pair.
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        var signedChallenge = byteArrayOf()
        val matches =
            try {
                signedChallenge =
                    Signature
                        .getInstance("SHA256withRSA")
                        .apply {
                            initSign(privateKey)
                            update(challenge)
                        }.sign()
                try {
                    Signature
                        .getInstance("SHA256withRSA")
                        .apply {
                            initVerify(publicKey)
                            update(challenge)
                        }.verify(signedChallenge)
                } catch (_: SignatureException) {
                    false
                }
            } finally {
                challenge.fill(0)
                signedChallenge.fill(0)
            }

        require(matches) { "JWT signing and verification keys do not match" }
        return Rs256TokenIssuer(privateKey, issuer, ttlSeconds, clock)
    }

    private fun loadPublicKey(path: Path): RSAPublicKey {
        require(Files.isRegularFile(path)) { "JWT verification public key file is missing" }
        require(Files.size(path) in 1..MAX_PEM_BYTES) {
            "JWT verification public key file size is invalid"
        }
        val pem = Files.readString(path, StandardCharsets.US_ASCII).trim()
        val match =
            PUBLIC_PEM.matchEntire(pem)
                ?: throw IllegalArgumentException("Expected an RSA X.509 public key PEM")
        val encoded = match.groupValues[1].replace("\r", "").replace("\n", "")
        val der =
            try {
                Base64.getDecoder().decode(encoded)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid JWT verification public key encoding")
            }

        return try {
            val key =
                try {
                    KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
                } catch (_: InvalidKeySpecException) {
                    throw IllegalArgumentException("Invalid RSA X.509 verification public key")
                }
            require(key is RSAPublicKey && key.modulus.bitLength() >= 2048) {
                "JWT verification RSA key must be at least 2048 bits"
            }
            key
        } finally {
            der.fill(0)
        }
    }

    private companion object {
        const val MAX_PEM_BYTES = 32_768L
        val PUBLIC_PEM =
            Regex(
                """\A-----BEGIN PUBLIC KEY-----\r?\n([A-Za-z0-9+/=\r\n]+)\r?\n-----END PUBLIC KEY-----\z""",
            )
    }
}
