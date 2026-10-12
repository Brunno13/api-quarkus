package br.com.brunno.features.users.domain

class UserEmailAlreadyExistsException(
    cause: Throwable? = null
) : RuntimeException(
    "User email already exists",
    cause
)
