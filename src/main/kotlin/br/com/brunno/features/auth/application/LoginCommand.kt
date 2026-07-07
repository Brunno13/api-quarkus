package br.com.brunno.features.auth.application

data class LoginCommand(
    val email: String,
    val password: String
)
