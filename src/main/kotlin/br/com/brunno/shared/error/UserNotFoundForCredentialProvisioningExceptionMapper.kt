package br.com.brunno.shared.error

import br.com.brunno.features.auth.application.UserNotFoundForCredentialProvisioningException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class UserNotFoundForCredentialProvisioningExceptionMapper :
    ExceptionMapper<UserNotFoundForCredentialProvisioningException> {

    override fun toResponse(
        exception: UserNotFoundForCredentialProvisioningException
    ): Response =
        Response.status(Response.Status.NOT_FOUND)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                ApiErrorResponse(
                    title = "Not Found",
                    status = 404,
                    detail = "User not found"
                )
            )
            .build()
}
