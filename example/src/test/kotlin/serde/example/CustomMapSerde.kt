package serde.example

import org.bson.BsonReader
import org.bson.BsonWriter
import serde.Serde

/** Custom serde for Map: stores as "k1=v1,k2=v2" instead of document. */
object CustomMapSerde : Serde<MutableMap<String, String>> {
    override fun read(reader: BsonReader): MutableMap<String, String> =
        reader.readString()
            .takeIf { it.isNotEmpty() }
            ?.split(",")
            ?.mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq < 0) null else part.substring(0, eq) to part.substring(eq + 1)
            }
            ?.toMap()
            ?.toMutableMap()
            ?: mutableMapOf()

    override fun write(writer: BsonWriter, value: MutableMap<String, String>) =
        writer.writeString(value.entries.joinToString(",") { "${it.key}=${it.value}" })
}
