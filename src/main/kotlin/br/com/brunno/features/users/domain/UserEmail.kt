package br.com.brunno.features.users.domain

import java.util.Locale

object UserEmail {

    fun normalize(email: String): String =
        email.trim().lowercase(Locale.ROOT)
}
