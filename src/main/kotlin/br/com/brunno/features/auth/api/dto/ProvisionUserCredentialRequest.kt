package br.com.brunno.features.auth.api.dto

data class ProvisionUserCredentialRequest(
    val password: String? = null,
) {
    override fun toString(): String = "ProvisionUserCredentialRequest(password=[REDACTED])"
}
