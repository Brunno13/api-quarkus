package br.com.brunno.features.auth.api

import br.com.brunno.features.auth.api.dto.LoginRequest
import br.com.brunno.features.auth.api.dto.LoginResponse
import br.com.brunno.features.auth.application.LoginUseCase
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Authentication endpoints")
class AuthResource(
    private val loginUseCase: LoginUseCase
) {

    @POST
    @Path("/login")
    @Operation(
        summary = "Login",
        description = "Authenticates a user and returns an access token. Currently uses a mock implementation."
    )
    fun login(@Valid request: LoginRequest): LoginResponse {
        val result = loginUseCase.execute(request.toCommand())
        return LoginResponse.from(result)
    }
}
