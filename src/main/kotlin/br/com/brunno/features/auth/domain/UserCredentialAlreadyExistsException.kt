package br.com.brunno.features.auth.domain

class UserCredentialAlreadyExistsException(
    cause: Throwable? = null,
) : RuntimeException(
        "Credentials already exist for this user",
        cause,
    )
