package serde.ext

import org.bson.BsonWriter
import org.bson.json.JsonWriter

fun BsonWriter.isJson() = this is JsonWriter
fun BsonWriter.isBson() = !isJson()

fun BsonWriter.writeEnum(value: Enum<*>) {
    writeString(value.name)
}

fun BsonWriter.writeDocument(writeFields: () -> Unit) {
    writeStartDocument()
    writeFields()
    writeEndDocument()
}

fun <E> BsonWriter.writeArray(coll: Iterable<E>, writeElement: (E) -> Unit) {
    writeStartArray()
    coll.forEach { writeElement(it) }
    writeEndArray()
}