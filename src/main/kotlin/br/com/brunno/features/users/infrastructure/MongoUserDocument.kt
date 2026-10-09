package br.com.brunno.features.users.infrastructure

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId

@MongoEntity(collection = "users")
class MongoUserDocument {

    @field:BsonId
    var id: String? = null

    var name: String = ""

    var email: String = ""

    var roles: List<String> = emptyList()
}
