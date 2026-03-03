package serde.ext

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.json.JsonReader

fun BsonReader.isJson() = this is JsonReader
fun BsonReader.isBson() = !isJson()

fun BsonReader.ifNotNullOrSkip(ifNotNull: () -> Unit) {
    if (currentBsonType == BsonType.NULL || currentBsonType == BsonType.UNDEFINED) {
        skipValue()
    } else {
        ifNotNull()
    }
}

fun BsonReader.readDocument(readElement: (String) -> Unit) {
    readStartDocument()
    while (readBsonType() != BsonType.END_OF_DOCUMENT) {
        val name = readName()
        ifNotNullOrSkip { readElement(name) }
    }
    readEndDocument()
}

fun BsonReader.readArray(readElement: () -> Unit) {
    readStartArray()
    while (readBsonType() != BsonType.END_OF_DOCUMENT) {
        ifNotNullOrSkip(readElement)
    }
    readEndArray()
}

inline fun <reified T : Enum<T>> BsonReader.readEnum() = enumValueOf<T>(readString())

fun <T> BsonReader.readDocumentFieldAndReset(name: String, readValue: BsonReader.() -> T): T? {
    val mark = mark
    readStartDocument()
    var result: T? = null
    try {
        while (readBsonType() != BsonType.END_OF_DOCUMENT) {
            if (readName() == name) {
                result = readValue()
                break
            } else {
                skipValue()
            }
        }
        return result
    } finally {
        mark.reset()
    }
}