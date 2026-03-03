package serde

import java.io.IOException
import java.io.StringWriter
import java.nio.ByteBuffer
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.io.BasicOutputBuffer
import org.bson.json.JsonReader
import org.bson.json.JsonWriter
import serde.ext.readArray
import serde.ext.writeArray

interface Serde<T : Any> {
    fun read(reader: BsonReader): T

    fun readList(reader: BsonReader): MutableList<T> =
        mutableListOf<T>().apply {
            reader.readArray { this += read(reader) }
        }

    fun write(writer: BsonWriter, value: T)

    fun writeList(writer: BsonWriter, coll: Iterable<T>) =
        writer.writeArray(coll) { write(writer, it) }

    fun fromBson(bson: ByteArray) = bson
        .let { ByteBuffer.wrap(it) }
        .let { BsonBinaryReader(it) }
        .let { read(it) }

    fun fromJson(json: String) = read(JsonReader(json))

    fun toBson(obj: T): ByteArray {
        val buffer = BasicOutputBuffer()
        val writer = BsonBinaryWriter(buffer)
        write(writer, obj)
        writer.close()
        return buffer.toByteArray()
    }

    fun toBsonDocument(obj: T): BsonDocument {
        val document = BsonDocument()
        val writer = BsonDocumentWriter(document)
        write(writer, obj)
        writer.close()
        return document
    }

    fun toJson(obj: T): String {
        val str = StringWriter()
        val writer = JsonWriter(str)
        write(writer, obj)
        try {
            str.close()
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        return str.toString()
    }

    fun copy(obj: T): T = fromBson(toBson(obj))
}
