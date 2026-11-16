package br.com.brunno.shared.error

import br.com.brunno.features.auth.application.LoginRateLimitExceededException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class LoginRateLimitExceededExceptionMapper :
    ExceptionMapper<LoginRateLimitExceededException> {
    override fun toResponse(exception: LoginRateLimitExceededException): Response =
        Response.status(429)
            .type(MediaType.APPLICATION_JSON)
            .header("Retry-After", exception.retryAfterSeconds.toString())
            .entity(
                ApiErrorResponse(
                    title = "Too Many Requests",
                    status = 429,
                    detail = "Too many login attempts"
                )
            )
            .build()
}
