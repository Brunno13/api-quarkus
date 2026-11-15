package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID

class ExternalRsaJwtIssuerFactoryTest {

    private val factory = ExternalRsaJwtIssuerFactory()

    @Test
    fun `matching external private and public keys issue verifiable RS256 JWT`(@TempDir dir: Path) {
        val pair = generateKeys()
        val issuer = factory.create(writePrivate(dir, pair), writePublic(dir, pair), "api-quarkus")
        val token = issuer.issue(user).accessToken
        val parts = token.split('.')
        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(pair.public)
            update("${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.US_ASCII))
        }

        assertEquals(3, parts.size)
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])))
        assertEquals(900L, issuer.issue(user).expiresInSeconds)
    }

    @Test
    fun `mismatched signing and verification key files are rejected`(@TempDir dir: Path) {
        val first = generateKeys()
        val second = generateKeys()
        val error = assertThrows(IllegalArgumentException::class.java) {
            factory.create(writePrivate(dir, first), writePublic(dir, second), "api-quarkus")
        }
        assertEquals("JWT signing and verification keys do not match", error.message)
    }

    @Test
    fun `missing private key fails closed`(@TempDir dir: Path) {
        val error = assertThrows(IllegalArgumentException::class.java) {
            factory.create(dir.resolve("absent.pem"), writePublic(dir, generateKeys()), "api-quarkus")
        }
        assertEquals("RSA private key file is missing", error.message)
    }

    @Test
    fun `missing verification public key fails closed`(@TempDir dir: Path) {
        val error = assertThrows(IllegalArgumentException::class.java) {
            factory.create(writePrivate(dir, generateKeys()), dir.resolve("absent.pem"), "api-quarkus")
        }
        assertEquals("JWT verification public key file is missing", error.message)
    }

    @Test
    fun `malformed public key is rejected without disclosing PEM content`(@TempDir dir: Path) {
        val marker = "PRIVATE_MARKER_DO_NOT_ECHO"
        val file = dir.resolve("invalid-public.pem")
        Files.writeString(file, "-----BEGIN PUBLIC KEY-----\n$marker\n-----END PUBLIC KEY-----\n")
        val error = assertThrows(IllegalArgumentException::class.java) {
            factory.create(writePrivate(dir, generateKeys()), file, "api-quarkus")
        }
        assertFalse(error.message.orEmpty().contains(marker))
    }

    @Test
    fun `undersized verification public key is rejected`(@TempDir dir: Path) {
        val error = assertThrows(IllegalArgumentException::class.java) {
            factory.create(writePrivate(dir, generateKeys()), writePublic(dir, generateKeys(1024)), "api-quarkus")
        }
        assertEquals("JWT verification RSA key must be at least 2048 bits", error.message)
    }

    @Test
    fun `invalid issuer and ttl are rejected before issuing token`(@TempDir dir: Path) {
        val pair = generateKeys()
        val privatePath = writePrivate(dir, pair)
        val publicPath = writePublic(dir, pair)
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(privatePath, publicPath, " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(privatePath, publicPath, "api-quarkus", 3601)
        }
    }

    private fun writePrivate(dir: Path, keys: KeyPair): Path = writePem(
        dir.resolve("signing.pem"), "PRIVATE KEY", keys.private.encoded
    )

    private fun writePublic(dir: Path, keys: KeyPair): Path = writePem(
        dir.resolve("verification.pem"), "PUBLIC KEY", keys.public.encoded
    )

    private fun writePem(path: Path, label: String, keyBytes: ByteArray): Path {
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray(StandardCharsets.US_ASCII))
            .encodeToString(keyBytes)
        Files.writeString(
            path, "-----BEGIN $label-----\n$encoded\n-----END $label-----\n",
            StandardCharsets.US_ASCII
        )
        return path
    }

    private fun generateKeys(bits: Int = 2048): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()

    private val user = User(
        UUID.fromString("00000000-0000-0000-0000-000000000234"),
        "Example User", "user@example.com", setOf(UserRole.USER)
    )
}
