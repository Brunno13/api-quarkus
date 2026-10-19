package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.application.PasswordHasher
import io.quarkus.elytron.security.common.BcryptUtil
import jakarta.enterprise.context.ApplicationScoped
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.spec.InvalidKeySpecException

@ApplicationScoped
class BcryptPasswordHasher : PasswordHasher {

    override fun hash(rawPassword: String): String {
        require(isValidInput(rawPassword)) {
            "Password must contain between 1 and 72 UTF-8 bytes and must not be blank"
        }

        return BcryptUtil.bcryptHash(rawPassword, 12)
    }

    override fun verify(
        rawPassword: String,
        encodedHash: String
    ): Boolean {
        if (!isValidInput(rawPassword)) {
            return false
        }

        return try {
            BcryptUtil.matches(rawPassword, encodedHash)
        } catch (_: IllegalArgumentException) {
            false
        } catch (exception: RuntimeException) {
            when (exception.cause) {
                is InvalidKeySpecException,
                is InvalidKeyException -> false
                else -> throw exception
            }
        }
    }

    private fun isValidInput(password: String): Boolean =
        password.isNotBlank() &&
                password.toByteArray(StandardCharsets.UTF_8).size <= 72
}
