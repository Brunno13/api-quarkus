package br.com.brunno.features.auth.cli

import br.com.brunno.features.auth.application.BootstrapFirstAdminCommand
import br.com.brunno.features.auth.application.InitialAdminAlreadyConfiguredException
import br.com.brunno.features.auth.application.InvalidInitialPasswordException
import br.com.brunno.features.users.domain.User
import br.com.brunno.features.users.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class BootstrapFirstAdminCliTest {
    private val db = "api_quarkus_it_bootstrap_cli"
    private val secret = "unit-only-password"

    @Test
    fun `creates administrator after explicit confirmation and clears both input arrays`() {
        val first = secret.toCharArray()
        val second = secret.toCharArray()
        val terminal = terminal(passwords = listOf(first, second))
        var captured: BootstrapFirstAdminCommand? = null
        val result =
            cli(terminal) { cmd ->
                captured = cmd
                admin()
            }.run(emptyArray(), "prod", db, false)

        assertEquals(0, result)
        assertEquals(secret, captured?.rawPassword)
        assertTrue(first.all { it == '\u0000' })
        assertTrue(second.all { it == '\u0000' })
        assertEquals(1, terminal.messages.count { it.startsWith("BOOTSTRAP=PASS") })
        assertTrue(terminal.errors.isEmpty())
        assertFalse(terminal.messages.joinToString().contains(secret))
        assertFalse(terminal.messages.joinToString().contains("admin@example.test"))
    }

    @Test
    fun `rejects extra args before any prompt`() {
        val terminal = terminal()
        assertDenied(terminal, arrayOf("--password=$secret"))
    }

    @Test
    fun `rejects non production profile before any prompt`() {
        val terminal = terminal()
        val result = cli(terminal).run(emptyArray(), "test", db, false)
        assertEquals(2, result)
        assertEquals(0, terminal.reads)
    }

    @Test
    fun `rejects enabled http listener before any prompt`() {
        val terminal = terminal()
        val result = cli(terminal).run(emptyArray(), "prod", db, true)
        assertEquals(2, result)
        assertEquals(0, terminal.reads)
    }

    @Test
    fun `rejects protected MongoDB database names`() {
        for (name in listOf("", "admin", "local", "config", "bad\nname")) {
            val terminal = terminal()
            val result = cli(terminal).run(emptyArray(), "prod", name, false)
            assertEquals(2, result)
            assertEquals(0, terminal.reads)
        }
    }

    @Test
    fun `refuses incorrect database confirmation without requesting password`() {
        val terminal = terminal(lines = listOf("bootstrap another_database"))
        val result = cli(terminal).run(emptyArray(), "prod", db, false)
        assertEquals(2, result)
        assertEquals(1, terminal.reads)
    }

    @Test
    fun `shows the exact database confirmation without quotes`() {
        val terminal = terminal(lines = listOf("not-confirmed"))
        val result =
            cli(terminal) { error("must not execute") }
                .run(emptyArray(), "prod", db, false)

        assertEquals(2, result)
        assertTrue(terminal.messages.any { it.contains("without quotation marks") })
        assertEquals("bootstrap $db", terminal.messages.last())
        assertEquals(listOf("Database confirmation: "), terminal.prompts)
    }

    @Test
    fun `aborts on missing administrator name before requesting password`() {
        val terminal = terminal(lines = listOf("bootstrap $db", null))
        val result =
            cli(terminal) { error("must not execute") }
                .run(emptyArray(), "prod", db, false)

        assertEquals(2, result)
        assertEquals(2, terminal.reads)
        assertEquals("Bootstrap cancelled: no administrator name supplied.", terminal.errors.single())
    }

    @Test
    fun `aborts on missing email before requesting password`() {
        val terminal = terminal(lines = listOf("bootstrap $db", "Admin", null))
        val result = cli(terminal).run(emptyArray(), "prod", db, false)
        assertEquals(2, result)
        assertEquals(3, terminal.reads)
    }

    @Test
    fun `mismatched passwords are cleared and do not call use case`() {
        val first = secret.toCharArray()
        val second = "different-password".toCharArray()
        val terminal = terminal(passwords = listOf(first, second))
        val result =
            cli(terminal) { error("must not execute") }
                .run(emptyArray(), "prod", db, false)
        assertEquals(2, result)
        assertTrue(first.all { it == '\u0000' })
        assertTrue(second.all { it == '\u0000' })
    }

    @Test
    fun `missing first password cancels before requesting confirmation`() {
        val terminal = terminal(passwords = listOf(null))
        val result =
            cli(terminal) { error("must not execute") }
                .run(emptyArray(), "prod", db, false)

        assertEquals(2, result)
        assertEquals(4, terminal.reads)
        assertEquals("Bootstrap cancelled: no password supplied.", terminal.errors.single())
    }

    @Test
    fun `missing password confirmation clears first password`() {
        val first = secret.toCharArray()
        val terminal = terminal(passwords = listOf(first, null))
        val result =
            cli(terminal) { error("must not execute") }
                .run(emptyArray(), "prod", db, false)
        assertEquals(2, result)
        assertTrue(first.all { it == '\u0000' })
    }

    @Test
    fun `consumed reservation produces safe denial and clears passwords`() {
        val first = secret.toCharArray()
        val second = secret.toCharArray()
        val terminal = terminal(passwords = listOf(first, second))
        val result =
            cli(terminal) { throw InitialAdminAlreadyConfiguredException() }
                .run(emptyArray(), "prod", db, false)
        assertEquals(2, result)
        assertTrue(terminal.errors.single().contains("already been configured"))
        assertTrue(first.all { it == '\u0000' } && second.all { it == '\u0000' })
    }

    @Test
    fun `invalid password produces safe denial`() {
        val terminal = terminal()
        val result =
            cli(terminal) { throw InvalidInitialPasswordException() }
                .run(emptyArray(), "prod", db, false)
        assertEquals(2, result)
        assertTrue(terminal.errors.single().contains("password"))
    }

    @Test
    fun `invalid administrator input is rejected without exposing credentials`() {
        val first = secret.toCharArray()
        val second = secret.toCharArray()
        val terminal = terminal(passwords = listOf(first, second))
        val result =
            cli(terminal) { throw IllegalArgumentException("sensitive=$secret") }
                .run(emptyArray(), "prod", db, false)

        assertEquals(2, result)
        assertEquals("Bootstrap denied: invalid administrator input.", terminal.errors.single())
        assertTrue(first.all { it == '\u0000' })
        assertTrue(second.all { it == '\u0000' })
        assertFalse((terminal.errors + terminal.messages).joinToString().contains(secret))
    }

    @Test
    fun `infrastructure failure never prints exception contents or retries`() {
        val terminal = terminal()
        var executions = 0
        val result =
            cli(terminal) {
                executions++
                throw IllegalStateException("MONGODB_URI=secret-in-error; password=$secret")
            }.run(emptyArray(), "prod", db, false)
        assertEquals(2, result)
        assertEquals(1, executions)
        assertFalse((terminal.errors + terminal.messages).joinToString().contains(secret))
        assertFalse((terminal.errors + terminal.messages).joinToString().contains("MONGODB_URI"))
        assertTrue(terminal.errors.single().contains("manual audit"))
    }

    private fun assertDenied(
        terminal: FakeTerminal,
        args: Array<String>,
    ) {
        val result = cli(terminal).run(args, "prod", db, false)
        assertEquals(2, result)
        assertEquals(0, terminal.reads)
        assertFalse(terminal.errors.joinToString().contains(secret))
    }

    private fun cli(
        terminal: FakeTerminal,
        action: (BootstrapFirstAdminCommand) -> User = { admin() },
    ) = BootstrapFirstAdminCli(action, terminal)

    private fun admin() =
        User(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "Admin",
            "admin@example.test",
            setOf(UserRole.ADMIN),
        )

    private fun terminal(
        lines: List<String?> = listOf("bootstrap $db", "Admin", "admin@example.test"),
        passwords: List<CharArray?> = listOf(secret.toCharArray(), secret.toCharArray()),
    ) = FakeTerminal(lines.toMutableList(), passwords.toMutableList())

    private class FakeTerminal(
        private val lines: MutableList<String?>,
        private val passwords: MutableList<CharArray?>,
    ) : BootstrapAdminTerminal {
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val prompts = mutableListOf<String>()
        var reads = 0

        override fun readLine(prompt: String): String? {
            prompts.add(prompt)
            reads++
            return lines.removeAt(0)
        }

        override fun readPassword(prompt: String): CharArray? {
            reads++
            return passwords.removeAt(0)
        }

        override fun info(message: String) {
            messages.add(message)
        }

        override fun error(message: String) {
            errors.add(message)
        }
    }
}
