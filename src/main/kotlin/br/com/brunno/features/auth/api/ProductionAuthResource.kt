package br.com.brunno.features.auth.api

import br.com.brunno.features.auth.api.dto.LoginRequest
import br.com.brunno.features.auth.api.dto.PersistedLoginResponse
import br.com.brunno.features.auth.application.LoginPersistedUserUseCase
import io.quarkus.arc.profile.IfBuildProfile
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/** Production-only login. The existing dev/test mock remains independent. */
@IfBuildProfile("prod")
@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Authentication endpoints")
class ProductionAuthResource(
    private val loginUseCase: LoginPersistedUserUseCase
) {
    @POST
    @Path("/login")
    @Operation(
        summary = "Login",
        description = "Authenticates a persisted user and issues a signed RS256 access token."
    )
    fun login(@Valid request: LoginRequest): PersistedLoginResponse =
        PersistedLoginResponse.from(
            loginUseCase.execute(request.email, request.password)
        )
}
