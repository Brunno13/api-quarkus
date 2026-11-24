package br.com.brunno.features.auth.api

import br.com.brunno.features.auth.api.dto.ProvisionUserCredentialRequest
import br.com.brunno.features.auth.application.InvalidInitialPasswordException
import br.com.brunno.features.auth.application.ProvisionUserCredentialUseCase
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.annotation.security.RolesAllowed
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.util.UUID

@IfBuildProperty(
    name = "app.persistence.mongodb.enabled",
    stringValue = "true",
)
@Path("/users/{id}/credentials")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Tag(name = "Credentials", description = "Administrative credential provisioning")
class UserCredentialResource(
    private val provisionUserCredentialUseCase: ProvisionUserCredentialUseCase,
) {
    @PUT
    @Operation(
        summary = "Provision initial user credentials",
        description = "Sets a password only when the user has no credentials. Requires ADMIN.",
    )
    fun provision(
        @PathParam("id") userId: UUID,
        request: ProvisionUserCredentialRequest?,
    ): Response {
        val password = request?.password ?: throw InvalidInitialPasswordException()

        provisionUserCredentialUseCase.execute(userId, password)

        return Response.noContent().build()
    }
}
