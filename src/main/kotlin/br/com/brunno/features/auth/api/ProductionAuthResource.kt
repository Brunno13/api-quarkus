package br.com.brunno.features.auth.api

import br.com.brunno.features.auth.api.dto.LoginRequest
import br.com.brunno.features.auth.api.dto.PersistedLoginResponse
import br.com.brunno.features.auth.application.LoginPersistedUserUseCase
import br.com.brunno.features.auth.application.LoginRateLimitAdmissionService
import io.quarkus.arc.profile.IfBuildProfile
import io.vertx.core.http.HttpServerRequest
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
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
    private val loginUseCase: LoginPersistedUserUseCase,
    private val admissionService: LoginRateLimitAdmissionService
) {
    @POST
    @Path("/login")
    @Operation(
        summary = "Login",
        description = "Authenticates a persisted user and issues a signed RS256 access token."
    )
    fun login(
        @Valid request: LoginRequest,
        @Context httpRequest: HttpServerRequest
    ): PersistedLoginResponse {
        // Quarkus uses the direct network peer unless the deployment explicitly
        // enables and restricts reverse-proxy forwarding. Never parse request
        // headers (Forwarded/X-Forwarded-For) in this resource.
        val verifiedIp = httpRequest.remoteAddress()?.hostAddress()
            ?.takeIf { it.isNotBlank() && it.length <= 64 }
            ?: throw IllegalStateException("Login client address is unavailable")

        // Reserve before BCrypt and before signing a token. A failed store or
        // missing peer address must not fall back to an unprotected login.
        admissionService.reserve(request.email, verifiedIp)
        return PersistedLoginResponse.from(
            loginUseCase.execute(request.email, request.password)
        )
    }
}
