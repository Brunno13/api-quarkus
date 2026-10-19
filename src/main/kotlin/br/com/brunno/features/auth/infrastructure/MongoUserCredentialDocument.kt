package br.com.brunno.features.auth.infrastructure

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId

@MongoEntity(collection = "user_credentials")
class MongoUserCredentialDocument {

    @field:BsonId
    var id: String? = null

    var passwordHash: String = ""
}
