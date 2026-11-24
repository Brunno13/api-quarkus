package br.com.brunno.features.auth.cli

import br.com.brunno.features.auth.application.BootstrapFirstAdminCommand
import br.com.brunno.features.auth.application.BootstrapFirstAdminUseCase
import br.com.brunno.features.auth.application.InitialAdminBootstrapStore
import br.com.brunno.features.auth.application.PasswordHasher
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain
import io.quarkus.runtime.configuration.ConfigUtils
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import org.eclipse.microprofile.config.ConfigProvider

/**
 * Select ONLY in a separately built administrative artifact using
 * quarkus.package.main-class=bootstrap-admin, with HTTP listener disabled.
 * Normal API builds retain Quarkus's generated HTTP entrypoint.
 */
@QuarkusMain(name = "bootstrap-admin")
class BootstrapFirstAdminMain : QuarkusApplication {
    @Inject
    lateinit var stores: Instance<InitialAdminBootstrapStore>

    @Inject
    lateinit var hashers: Instance<PasswordHasher>

    override fun run(vararg args: String): Int {
        val terminal = SystemBootstrapAdminTerminal.open()
        if (terminal == null) {
            System.err.println("Bootstrap requires an interactive system console; stdin is not accepted.")
            return 2
        }

        val config = ConfigProvider.getConfig()
        val databaseName =
            config
                .getOptionalValue(
                    "quarkus.mongodb.database",
                    String::class.java,
                ).orElse("")
        val httpHostEnabled =
            !config
                .getOptionalValue(
                    "quarkus.http.host-enabled",
                    String::class.java,
                ).orElse("true")
                .equals("false", ignoreCase = true)

        // Instance<T> permits dev/test profiles with no MongoDB bean; prod is checked before access.
        val action: (BootstrapFirstAdminCommand) -> br.com.brunno.features.users.domain.User = { command ->
            BootstrapFirstAdminUseCase(stores.get(), hashers.get()).execute(command)
        }
        return BootstrapFirstAdminCli(action, terminal).run(
            args = args,
            activeProfile = ConfigUtils.getProfiles().singleOrNull().orEmpty(),
            databaseName = databaseName,
            httpHostEnabled = httpHostEnabled,
        )
    }
}
