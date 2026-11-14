package br.com.brunno.shared.error

import br.com.brunno.features.auth.application.InvalidInitialPasswordException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class InvalidInitialPasswordExceptionMapper :
    ExceptionMapper<InvalidInitialPasswordException> {

    override fun toResponse(exception: InvalidInitialPasswordException): Response =
        Response.status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                ApiErrorResponse(
                    title = "Bad Request",
                    status = 400,
                    detail = "Initial password is invalid"
                )
            )
            .build()
}
