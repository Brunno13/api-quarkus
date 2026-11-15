package br.com.brunno.features.auth.application

import io.quarkus.arc.profile.IfBuildProfile
import jakarta.enterprise.context.ApplicationScoped

/** Production-only orchestration: never replaces the dev/test mock login. */
@ApplicationScoped
@IfBuildProfile("prod")
class LoginPersistedUserUseCase(
    private val authenticate: AuthenticatePersistedUserUseCase,
    private val tokenIssuer: TokenIssuer
) {
    fun execute(email: String, rawPassword: String): AuthenticatedSession {
        val user = authenticate.execute(email, rawPassword)
        val token = tokenIssuer.issue(user)
        return AuthenticatedSession(user, token)
    }
}
