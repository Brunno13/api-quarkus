package br.com.brunno.features.users.api

import br.com.brunno.features.users.api.dto.UserResponse
import br.com.brunno.features.users.api.dto.CreateUserRequest
import br.com.brunno.features.users.application.GetUserByIdUseCase
import br.com.brunno.features.users.application.CreateUserUseCase
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.annotation.security.RolesAllowed
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.core.Response
import jakarta.validation.Valid
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.util.UUID

@IfBuildProperty(
    name = "app.persistence.mongodb.enabled",
    stringValue = "true"
)
@Path("/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Tag(name = "Users", description = "User administration endpoints")
class UserResource(
    private val getUserByIdUseCase: GetUserByIdUseCase,
    private val createUserUseCase: CreateUserUseCase
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

    @POST
    @Operation(
        summary = "Create user",
        description = "Creates a user with the USER role. Requires the ADMIN role."
    )
    fun create(@Valid request: CreateUserRequest): Response {
        val user = createUserUseCase.execute(request.toCommand())

        return Response
            .status(Response.Status.CREATED)
            .entity(UserResponse.from(user))
            .build()
    }
}
