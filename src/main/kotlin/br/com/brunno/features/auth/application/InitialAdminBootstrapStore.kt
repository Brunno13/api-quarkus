package br.com.brunno.features.auth.application

import br.com.brunno.features.auth.domain.UserCredential
import br.com.brunno.features.users.domain.User

/**
 * Privileged, one-time persistence boundary. Not an HTTP or user-facing API.
 *
 * The implementation MUST prevent two concurrent bootstrap executions from
 * granting ADMIN, reject bootstrap when an ADMIN already exists or the initial
 * claim has been consumed, and never make an ADMIN account visible before its
 * credential is durably stored.
 * On incomplete writes it must fail closed and require explicit recovery.
 * These guarantees must be verified against the chosen MongoDB topology.
 */
interface InitialAdminBootstrapStore {
    @Throws(InitialAdminAlreadyConfiguredException::class)
    fun createFirstAdmin(user: User, credential: UserCredential): User
}
