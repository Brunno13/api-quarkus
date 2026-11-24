package br.com.brunno.shared.error

import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import br.com.brunno.features.auth.application.UserNotFoundForCredentialProvisioningException as MissingCredentialUser

@Provider
class UserNotFoundForCredentialProvisioningExceptionMapper : ExceptionMapper<MissingCredentialUser> {
    override fun toResponse(exception: MissingCredentialUser): Response =
        Response
            .status(Response.Status.NOT_FOUND)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                ApiErrorResponse(
                    title = "Not Found",
                    status = 404,
                    detail = "User not found",
                ),
            ).build()
}
