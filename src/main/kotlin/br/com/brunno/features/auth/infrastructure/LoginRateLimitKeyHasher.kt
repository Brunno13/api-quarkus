package br.com.brunno.features.auth.infrastructure

import java.nio.charset.StandardCharsets
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Keeps email/IP out of MongoDB identifiers. The production secret must be
 * external, stable across replicas and at least 256 bits of random material.
 * Never reuse this secret as a JWT signing key or emit it in logs.
 */
internal class LoginRateLimitKeyHasher(
    secret: ByteArray,
) {
    private val key =
        secret.copyOf().also {
            require(it.size >= 32) { "Rate-limit HMAC key must contain at least 32 bytes" }
        }

    fun keyFor(
        dimension: String,
        identifier: String,
        bucket: Long,
    ): String {
        require(dimension == "identity" || dimension == "origin") {
            "Unsupported rate-limit dimension"
        }
        require(identifier.isNotBlank()) { "Rate-limit identifier must not be blank" }

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val digest =
            mac.doFinal(
                ("v1\u0000$dimension\u0000$identifier")
                    .toByteArray(StandardCharsets.UTF_8),
            )
        return "v1:$dimension:$bucket:${HexFormat.of().formatHex(digest)}"
    }
}
