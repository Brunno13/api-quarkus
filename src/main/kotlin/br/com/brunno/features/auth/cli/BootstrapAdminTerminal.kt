package br.com.brunno.features.auth.cli

import java.io.Console

/** Privileged interactive input. Never fall back to echoed stdin or CLI password args. */
interface BootstrapAdminTerminal {
    fun readLine(prompt: String): String?
    fun readPassword(prompt: String): CharArray?
    fun info(message: String)
    fun error(message: String)
}

class SystemBootstrapAdminTerminal private constructor(
    private val console: Console
) : BootstrapAdminTerminal {
    override fun readLine(prompt: String): String? =
        console.readLine("%s", prompt)

    override fun readPassword(prompt: String): CharArray? =
        console.readPassword("%s", prompt)

    override fun info(message: String) {
        console.printf("%s%n", message)
    }

    override fun error(message: String) {
        console.printf("%s%n", message)
    }

    companion object {
        fun open(): SystemBootstrapAdminTerminal? =
            System.console()?.let(::SystemBootstrapAdminTerminal)
    }
}
