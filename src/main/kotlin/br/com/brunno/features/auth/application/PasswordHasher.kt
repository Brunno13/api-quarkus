package br.com.brunno.features.auth.application

interface PasswordHasher {
    fun hash(rawPassword: String): String

    fun verify(rawPassword: String, encodedHash: String): Boolean
}
