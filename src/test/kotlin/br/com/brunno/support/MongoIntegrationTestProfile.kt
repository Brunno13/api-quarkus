package br.com.brunno.support

import io.quarkus.test.junit.QuarkusTestProfile

class MongoIntegrationTestProfile : QuarkusTestProfile {

    override fun getConfigOverrides(): Map<String, String> {
        val uri = requiredEnvironment("MONGODB_URI")
        val database = requiredEnvironment("MONGODB_DATABASE")

        require(database.startsWith("api_quarkus_it_")) {
            "Integration tests require a dedicated database " +
                "with the api_quarkus_it_ prefix"
        }

        return mapOf(
            "app.persistence.mongodb.enabled" to "true",
            "quarkus.mongodb.active" to "true",
            "quarkus.mongodb.devservices.enabled" to "false",
            "quarkus.mongodb.health.enabled" to "false",
            "quarkus.mongodb.connection-string" to uri,
            "quarkus.mongodb.database" to database
        )
    }

    private fun requiredEnvironment(name: String): String =
        System.getenv(name)
            ?.takeIf { it.isNotBlank() }
            ?: error("$name is required for MongoDB integration tests")
}
