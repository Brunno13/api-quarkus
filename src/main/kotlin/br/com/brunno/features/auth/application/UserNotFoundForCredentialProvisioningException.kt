package br.com.brunno.features.auth.application

class UserNotFoundForCredentialProvisioningException :
    RuntimeException(
        "User not found",
    )
