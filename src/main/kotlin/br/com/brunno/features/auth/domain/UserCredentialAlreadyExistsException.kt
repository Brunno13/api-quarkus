package br.com.brunno.features.auth.domain

class UserCredentialAlreadyExistsException(
    cause: Throwable
) : RuntimeException(
    "Credentials already exist for this user",
    cause
)
