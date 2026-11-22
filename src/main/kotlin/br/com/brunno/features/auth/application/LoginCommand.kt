package br.com.brunno.features.auth.application

data class LoginCommand(
    val email: String,
    val password: String,
) {
    // Prevent accidental disclosure of credentials in application logs.
    override fun toString(): String = "LoginCommand(email=[REDACTED], password=[REDACTED])"
}
