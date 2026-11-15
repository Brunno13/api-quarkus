package br.com.brunno.features.auth.infrastructure

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Loads an externally supplied, unencrypted PKCS#8 RSA private key from PEM.
 *
 * Deliberately not a CDI bean. Production configuration and lifecycle wiring
 * are a separate checkpoint. No key material or fallback key is bundled.
 */
class Pkcs8RsaPrivateKeyLoader {

    fun load(path: Path): RSAPrivateKey {
        require(Files.isRegularFile(path)) { "RSA private key file is missing" }
        require(Files.size(path) in 1..MAX_PEM_BYTES) {
            "RSA private key file size is invalid"
        }

        val pem = Files.readString(path, StandardCharsets.US_ASCII).trim()
        val match = PKCS8_PEM.matchEntire(pem)
            ?: throw IllegalArgumentException("Expected an unencrypted PKCS#8 private key PEM")

        val encoded = match.groupValues[1].replace("\r", "").replace("\n", "")
        val der = try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid PKCS#8 private key encoding")
        }

        try {
            val key = try {
                KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(der))
            } catch (_: InvalidKeySpecException) {
                throw IllegalArgumentException("Invalid RSA PKCS#8 private key")
            }

            require(key is RSAPrivateKey && key.modulus.bitLength() >= 2048) {
                "RSA private key must be at least 2048 bits"
            }
            return key
        } finally {
            der.fill(0)
        }
    }

    private companion object {
        const val MAX_PEM_BYTES = 32_768L

        val PKCS8_PEM = Regex(
            """\A-----BEGIN PRIVATE KEY-----\r?\n([A-Za-z0-9+/=\r\n]+)\r?\n-----END PRIVATE KEY-----\z"""
        )
    }
}
