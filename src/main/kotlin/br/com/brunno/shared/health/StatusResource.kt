package br.com.brunno.shared.health

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/status")
class StatusResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun status(): StatusResponse {
        return StatusResponse(
            name = "api-quarkus",
            status = "UP"
        )
    }
}

data class StatusResponse(
    val name: String,
    val status: String
)
