package br.com.brunno.shared.error

import br.com.brunno.features.users.application.InvalidUserEmailFormatException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class InvalidUserEmailFormatExceptionMapper :
    ExceptionMapper<InvalidUserEmailFormatException> {

    override fun toResponse(
        exception: InvalidUserEmailFormatException
    ): Response {
        return Response
            .status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                ApiErrorResponse(
                    title = "Bad Request",
                    status = 400,
                    detail = exception.message ?: "Invalid user email format"
                )
            )
            .build()
    }
}
