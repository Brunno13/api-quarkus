package br.com.brunno.shared.error

import br.com.brunno.features.auth.application.InvalidCredentialsException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class InvalidCredentialsExceptionMapper :
    ExceptionMapper<InvalidCredentialsException> {

    override fun toResponse(
        exception: InvalidCredentialsException
    ): Response {
        return Response
            .status(Response.Status.UNAUTHORIZED)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                ApiErrorResponse(
                    title = "Unauthorized",
                    status = 401,
                    detail = "Invalid credentials"
                )
            )
            .build()
    }
}
