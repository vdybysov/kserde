package serde

import org.bson.BsonWriter
import serde.ext.writeDocument

interface ObjectSerde<T : Any> : Serde<T> {
    fun writeFields(writer: BsonWriter, obj: T)
    override fun write(writer: BsonWriter, value: T) {
        writer.writeDocument { writeFields(writer, value) }
    }
}