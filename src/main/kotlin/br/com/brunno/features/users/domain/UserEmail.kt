package br.com.brunno.features.users.domain

import java.util.Locale

object UserEmail {

    private val basicEmailPattern =
        Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    fun normalize(email: String): String =
        email.trim().lowercase(Locale.ROOT)

    fun isValid(email: String): Boolean =
        basicEmailPattern.matches(normalize(email))
}
