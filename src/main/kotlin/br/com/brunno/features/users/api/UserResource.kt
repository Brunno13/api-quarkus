package br.com.brunno.features.users.api

import br.com.brunno.features.users.api.dto.UserResponse
import br.com.brunno.features.users.application.GetUserByIdUseCase
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.annotation.security.RolesAllowed
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.util.UUID

@IfBuildProperty(
    name = "app.persistence.mongodb.enabled",
    stringValue = "true"
)
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Tag(name = "Users", description = "User administration endpoints")
class UserResource(
    private val getUserByIdUseCase: GetUserByIdUseCase
) {

    @GET
    @Path("/{id}")
    @Operation(
        summary = "Get user by ID",
        description = "Returns a user by UUID. Requires the ADMIN role."
    )
    fun getById(@PathParam("id") id: UUID): UserResponse {
        val user = getUserByIdUseCase.execute(id)
            ?: throw NotFoundException("User not found")

        return UserResponse.from(user)
    }
}
