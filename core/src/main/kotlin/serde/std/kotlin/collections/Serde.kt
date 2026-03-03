package serde.std.kotlin.collections

import org.bson.BsonReader
import org.bson.BsonWriter
import serde.Serde
import serde.ext.readArray
import serde.ext.readDocument
import serde.ext.writeArray
import serde.ext.writeDocument

object EmptyListSerde : Serde<List<Any>> {
    override fun read(reader: BsonReader): List<Any> =
        emptyList<Any>().apply {
            with(reader) {
                readArray { skipValue() }
            }
        }

    override fun write(writer: BsonWriter, value: List<Any>) {
        writer.writeArray(emptyList<Any>()) {}
    }

}

object ListSerde {
    fun <T> read(reader: BsonReader, readElement: () -> T): List<T> {
        val result = mutableListOf<T>()
        reader.readArray { result += readElement() }
        return result.toList()
    }

    fun <T> write(writer: BsonWriter, value: List<T>, writeElement: (T) -> Unit) {
        writer.writeArray(value, writeElement)
    }
}

object MutableListSerde {
    fun <T> read(reader: BsonReader, readElement: () -> T): MutableList<T> =
        mutableListOf<T>().apply {
            reader.readArray { this += readElement() }
        }

    fun <T> write(writer: BsonWriter, value: List<T>, writeElement: (T) -> Unit) =
        writer.writeArray(value, writeElement)
}

object SetSerde {
    fun <T> read(reader: BsonReader, readElement: () -> T): Set<T> =
        mutableSetOf<T>().apply {
            reader.readArray { this += readElement() }
        }.toSet()

    fun <T> write(writer: BsonWriter, value: Set<T>, writeElement: (T) -> Unit) =
        writer.writeArray(value, writeElement)
}

object MutableSetSerde {
    fun <T> read(reader: BsonReader, readElement: () -> T): MutableSet<T> =
        SetSerde.read(reader, readElement).toMutableSet()

    fun <T> write(writer: BsonWriter, value: MutableSet<T>, writeElement: (T) -> Unit) =
        SetSerde.write(writer, value, writeElement)
}

object MutableMapSerde {
    fun <K, V> read(
        reader: BsonReader,
        mapKey: (String) -> K,
        readValue: BsonReader.() -> V
    ): MutableMap<K, V> = with(reader) {
        mutableMapOf<K, V>().apply {
            reader.readDocument {
                this[mapKey(it)] = readValue()
            }
        }
    }

    fun <K, V> write(
        writer: BsonWriter,
        value: Map<K, V>,
        mapKey: (K) -> String,
        writeValue: BsonWriter.(V) -> Unit
    ) = with(writer) {
        writeDocument {
            value.forEach { (key, value) ->
                writeName(mapKey(key))
                writeValue(value)
            }
        }
    }
}

object MapSerde {
    fun <K, V> read(
        reader: BsonReader,
        mapKey: (String) -> K,
        readValue: BsonReader.() -> V
    ): Map<K, V> =
        MutableMapSerde.read(reader, mapKey, readValue).toMap()

    fun <K, V> write(
        writer: BsonWriter,
        value: Map<K, V>,
        mapKey: (K) -> String,
        writeValue: BsonWriter.(V) -> Unit
    ) = MutableMapSerde.write(writer, value, mapKey, writeValue)
}