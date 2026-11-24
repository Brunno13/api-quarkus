package br.com.brunno.features.auth.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.util.Base64

class Pkcs8RsaPrivateKeyLoaderTest {
    private val loader = Pkcs8RsaPrivateKeyLoader()

    @Test
    fun `loads generated RSA PKCS8 key from external PEM file`(
        @TempDir directory: Path,
    ) {
        val pair = generateRsaKeys(2048)
        val privateKey = loader.load(writePem(directory, pair))

        assertEquals((pair.private as RSAPrivateKey).modulus, privateKey.modulus)
        assertTrue(privateKey.modulus.bitLength() >= 2048)
    }

    @Test
    fun `accepts Windows CRLF encoded PEM`(
        @TempDir directory: Path,
    ) {
        val pair = generateRsaKeys(2048)
        val path = writePem(directory, pair)
        val content = Files.readString(path, StandardCharsets.US_ASCII)
        Files.writeString(path, content.replace("\n", "\r\n"), StandardCharsets.US_ASCII)

        assertEquals((pair.private as RSAPrivateKey).modulus, loader.load(path).modulus)
    }

    @Test
    fun `fails when key file is missing`(
        @TempDir directory: Path,
    ) {
        assertThrows(IllegalArgumentException::class.java) {
            loader.load(directory.resolve("missing.pem"))
        }
    }

    @Test
    fun `rejects PKCS1 header instead of PKCS8`(
        @TempDir directory: Path,
    ) {
        val pair = generateRsaKeys(2048)
        val path = writePem(directory, pair)
        val pem =
            Files
                .readString(path, StandardCharsets.US_ASCII)
                .replace("BEGIN PRIVATE KEY", "BEGIN RSA PRIVATE KEY")
                .replace("END PRIVATE KEY", "END RSA PRIVATE KEY")
        Files.writeString(path, pem, StandardCharsets.US_ASCII)

        assertThrows(IllegalArgumentException::class.java) { loader.load(path) }
    }

    @Test
    fun `rejects invalid PEM base64 without exposing key material`(
        @TempDir directory: Path,
    ) {
        val marker = "NOT_A_PRIVATE_KEY_12345"
        val path = directory.resolve("malformed.pem")
        Files.writeString(
            path,
            "-----BEGIN PRIVATE KEY-----\n$marker\n-----END PRIVATE KEY-----\n",
            StandardCharsets.US_ASCII,
        )

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                loader.load(path)
            }
        assertTrue(!error.message.orEmpty().contains(marker))
    }

    @Test
    fun `rejects invalid base64 inside a structurally valid PKCS8 PEM`(
        @TempDir directory: Path,
    ) {
        val path = directory.resolve("invalid-base64.pem")
        Files.writeString(
            path,
            "-----BEGIN PRIVATE KEY-----\nA\n-----END PRIVATE KEY-----\n",
            StandardCharsets.US_ASCII,
        )

        val error = assertThrows(IllegalArgumentException::class.java) { loader.load(path) }
        assertEquals("Invalid PKCS#8 private key encoding", error.message)
    }

    @Test
    fun `rejects invalid DER after decoding a structurally valid PKCS8 PEM`(
        @TempDir directory: Path,
    ) {
        val path = directory.resolve("invalid-der.pem")
        Files.writeString(
            path,
            "-----BEGIN PRIVATE KEY-----\nAA==\n-----END PRIVATE KEY-----\n",
            StandardCharsets.US_ASCII,
        )

        val error = assertThrows(IllegalArgumentException::class.java) { loader.load(path) }
        assertEquals("Invalid RSA PKCS#8 private key", error.message)
    }

    @Test
    fun `rejects undersized RSA private key`(
        @TempDir directory: Path,
    ) {
        val path = writePem(directory, generateRsaKeys(1024))
        assertThrows(IllegalArgumentException::class.java) { loader.load(path) }
    }

    @Test
    fun `rejects oversized key files`(
        @TempDir directory: Path,
    ) {
        val path = directory.resolve("oversized.pem")
        Files.writeString(path, "A".repeat(32_769), StandardCharsets.US_ASCII)
        assertThrows(IllegalArgumentException::class.java) { loader.load(path) }
    }

    private fun writePem(
        directory: Path,
        pair: KeyPair,
    ): Path {
        val encoded =
            Base64
                .getMimeEncoder(64, "\n".toByteArray(StandardCharsets.US_ASCII))
                .encodeToString(pair.private.encoded)
        val path = directory.resolve("private-key.pem")
        Files.writeString(
            path,
            "-----BEGIN PRIVATE KEY-----\n$encoded\n-----END PRIVATE KEY-----\n",
            StandardCharsets.US_ASCII,
        )
        return path
    }

    private fun generateRsaKeys(bits: Int): KeyPair =
        KeyPairGenerator
            .getInstance("RSA")
            .apply { initialize(bits) }
            .generateKeyPair()
}
