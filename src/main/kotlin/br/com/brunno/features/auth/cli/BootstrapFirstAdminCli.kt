package br.com.brunno.features.auth.cli

import br.com.brunno.features.auth.application.BootstrapFirstAdminCommand
import br.com.brunno.features.auth.application.InitialAdminAlreadyConfiguredException
import br.com.brunno.features.auth.application.InvalidInitialPasswordException
import br.com.brunno.features.users.application.InvalidUserEmailFormatException
import br.com.brunno.features.users.domain.User

/**
 * Interactive, one-time administrative flow. Nothing here is an HTTP endpoint.
 * It deliberately never prints a user-supplied value or exception message.
 */
class BootstrapFirstAdminCli(
    private val createAdmin: (BootstrapFirstAdminCommand) -> User,
    private val terminal: BootstrapAdminTerminal,
) {
    fun run(
        args: Array<out String>,
        activeProfile: String,
        databaseName: String,
        httpHostEnabled: Boolean,
    ): Int {
        if (args.isNotEmpty()) {
            return fail("Arguments are not accepted. Enter all values interactively.")
        }
        if (activeProfile != "prod") {
            return fail("Bootstrap requires a production-profile build.")
        }
        if (httpHostEnabled) {
            return fail("HTTP listener must be disabled for the bootstrap build.")
        }
        if (!SAFE_DATABASE_NAME.matches(databaseName) || databaseName in RESERVED_DATABASE_NAMES) {
            return fail("Refusing bootstrap with an invalid MongoDB database name.")
        }

        terminal.info("INITIAL ADMIN BOOTSTRAP (one-time, irreversible reservation)")
        terminal.info("Target MongoDB database: $databaseName")
        terminal.info("To confirm the database, enter the next line exactly (without quotation marks):")
        terminal.info("bootstrap $databaseName")
        val confirmation = terminal.readLine("Database confirmation: ")
        if (confirmation != "bootstrap $databaseName") {
            return fail("Bootstrap cancelled: target database not confirmed.")
        }

        val name =
            terminal.readLine("Administrator name: ")
                ?: return fail("Bootstrap cancelled: no administrator name supplied.")
        val email =
            terminal.readLine("Administrator email: ")
                ?: return fail("Bootstrap cancelled: no administrator email supplied.")

        var password: CharArray? = null
        var repeatedPassword: CharArray? = null
        try {
            password = terminal.readPassword("Password (hidden): ")
                ?: return fail("Bootstrap cancelled: no password supplied.")
            repeatedPassword = terminal.readPassword("Confirm password (hidden): ")
                ?: return fail("Bootstrap cancelled: password confirmation missing.")
            if (!password.contentEquals(repeatedPassword)) {
                return fail("Bootstrap cancelled: passwords do not match.")
            }

            // Existing PasswordHasher API requires String; char[]s are wiped below.
            val created = createAdmin(BootstrapFirstAdminCommand(name, email, String(password)))
            terminal.info("BOOTSTRAP=PASS USER_ID=${created.id}")
            return 0
        } catch (_: InitialAdminAlreadyConfiguredException) {
            return fail("Bootstrap denied: the first ADMIN has already been configured or reserved.")
        } catch (_: InvalidInitialPasswordException) {
            return fail("Bootstrap denied: password does not satisfy the configured policy.")
        } catch (_: InvalidUserEmailFormatException) {
            return fail("Bootstrap denied: invalid administrator email.")
        } catch (_: IllegalArgumentException) {
            return fail("Bootstrap denied: invalid administrator input.")
        } catch (_: Exception) {
            // The reservation may already be consumed. Never log exception details or retry.
            return fail("Bootstrap failed. Reservation may be consumed; manual audit is required.")
        } finally {
            password?.fill('\u0000')
            repeatedPassword?.fill('\u0000')
        }
    }

    private fun fail(message: String): Int {
        terminal.error(message)
        return 2
    }

    private companion object {
        val RESERVED_DATABASE_NAMES = setOf("admin", "local", "config")
        val SAFE_DATABASE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")
    }
}
