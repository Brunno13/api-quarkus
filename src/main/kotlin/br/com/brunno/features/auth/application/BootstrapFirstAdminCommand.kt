package br.com.brunno.features.auth.application

/**
 * Deliberately not a data class: generated toString/copy/component methods
 * must not expose or duplicate the initial administrator password.
 */
class BootstrapFirstAdminCommand(
    val name: String,
    val email: String,
    val rawPassword: String
) {
    override fun toString(): String =
        "BootstrapFirstAdminCommand(name=[REDACTED], email=[REDACTED], rawPassword=[REDACTED])"
}
