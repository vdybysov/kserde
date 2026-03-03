package serde.std.org.bson.types

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.types.ObjectId
import serde.Serde
import serde.ext.isJson

object ObjectIdSerde : Serde<ObjectId> {
    override fun read(reader: BsonReader): ObjectId = with(reader) {
        when {
            currentBsonType == BsonType.STRING -> ObjectId(reader.readString())
            else -> reader.readObjectId()
        }
    }

    fun write(writer: BsonWriter, value: String) = with(writer) {
        when {
            isJson() -> writeString(value)
            else -> writeObjectId(ObjectId(value))
        }
    }

    override fun write(writer: BsonWriter, value: ObjectId) = with(writer) {
        when {
            isJson() -> writeString(value.toString())
            else -> writeObjectId(value)
        }
    }
}