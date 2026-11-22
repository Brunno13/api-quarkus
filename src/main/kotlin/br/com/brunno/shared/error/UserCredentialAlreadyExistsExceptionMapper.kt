package br.com.brunno.shared.error

import br.com.brunno.features.auth.domain.UserCredentialAlreadyExistsException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class UserCredentialAlreadyExistsExceptionMapper : ExceptionMapper<UserCredentialAlreadyExistsException> {
    override fun toResponse(exception: UserCredentialAlreadyExistsException): Response =
        Response
            .status(Response.Status.CONFLICT)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                ApiErrorResponse(
                    title = "Conflict",
                    status = 409,
                    detail = "Credentials already exist for this user",
                ),
            ).build()
}
