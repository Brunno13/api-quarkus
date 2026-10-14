package br.com.brunno.shared.error

import br.com.brunno.features.users.domain.UserEmailAlreadyExistsException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class UserEmailAlreadyExistsExceptionMapper :
    ExceptionMapper<UserEmailAlreadyExistsException> {

    override fun toResponse(
        exception: UserEmailAlreadyExistsException
    ): Response {
        return Response
            .status(Response.Status.CONFLICT)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                ApiErrorResponse(
                    title = "Conflict",
                    status = 409,
                    detail = exception.message ?: "User email already exists"
                )
            )
            .build()
    }
}
