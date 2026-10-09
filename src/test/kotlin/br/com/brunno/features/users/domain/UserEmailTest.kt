package br.com.brunno.features.users.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserEmailTest {

    @Test
    fun `normalizes whitespace and casing`() {
        val result = UserEmail.normalize("  ADMIN@Example.COM  ")

        assertEquals("admin@example.com", result)
    }

    @Test
    fun `normalization is idempotent`() {
        val normalized = UserEmail.normalize("  USER@Example.COM ")

        assertEquals(
            normalized,
            UserEmail.normalize(normalized)
        )
    }
}
