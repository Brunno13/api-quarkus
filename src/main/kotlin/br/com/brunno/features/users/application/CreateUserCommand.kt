package br.com.brunno.features.users.application

data class CreateUserCommand(
    val name: String,
    val email: String
)
