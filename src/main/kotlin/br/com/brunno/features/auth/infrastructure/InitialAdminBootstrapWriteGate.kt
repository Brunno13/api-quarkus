package br.com.brunno.features.auth.infrastructure

import br.com.brunno.features.auth.domain.UserCredential

/**
 * Infrastructure-only steps for a durable, one-time initial ADMIN claim.
 * A successful reservation must never be automatically released after an error.
 */
interface InitialAdminBootstrapWriteGate {
    fun reserveOnce()
    fun adminAlreadyExists(): Boolean
    fun persistCredentialDurably(credential: UserCredential)
}
