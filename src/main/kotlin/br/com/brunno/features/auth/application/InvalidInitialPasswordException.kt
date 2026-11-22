package br.com.brunno.features.auth.application

class InvalidInitialPasswordException :
    RuntimeException(
        "Initial password is invalid",
    )
