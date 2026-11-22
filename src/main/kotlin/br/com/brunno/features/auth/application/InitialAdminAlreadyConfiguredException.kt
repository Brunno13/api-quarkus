package br.com.brunno.features.auth.application

class InitialAdminAlreadyConfiguredException :
    IllegalStateException(
        "Initial administrator bootstrap is already configured",
    )
