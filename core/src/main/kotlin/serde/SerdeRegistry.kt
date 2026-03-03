package serde

import java.util.concurrent.ConcurrentHashMap
import org.bson.BsonReader
import org.bson.BsonWriter
import serde.ext.getSerdeClassName
import serde.ext.readArray
import serde.ext.writeArray
import serde.std.kotlin.collections.EmptyListSerde

@Suppress("UNCHECKED_CAST", "NO_REFLECTION_IN_CLASS_PATH")
class SerdeRegistry {

    companion object {
        @JvmField
        val Default = SerdeRegistry()
    }

    private val serde = ConcurrentHashMap<Class<*>, Serde<*>>()
    private val listSerde = ConcurrentHashMap<Class<*>, Serde<*>>()

    fun <T : Any> getSerde(type: Class<T>): Serde<T> =
        serde.computeIfAbsent(type) {
            type.getSerdeClassName()
                .let { (packageName, simpleName) ->
                    val name = "$packageName.$simpleName"
                    runCatching { Class.forName(name) }
                        .recoverCatching { Class.forName("${PackageNames.STD}.$name") }
                        .recoverCatching { Class.forName("${PackageNames.STD}.kotlin.$simpleName") }
                        .getOrNull()
                }
                ?.kotlin
                ?.objectInstance as? Serde<T>
                ?: error("No serde for ${it.name}")
        } as Serde<T>

    fun <T : Any> getSerde(obj: T): Serde<T> =
        when (obj) {
            is List<*> -> (
                obj.firstOrNull()
                    ?.javaClass
                    ?.let { elementType ->
                        listSerde.computeIfAbsent(elementType) {
                            val elementSerde = getSerde(elementType)
                            object : Serde<List<Any>> {
                                override fun read(reader: BsonReader): List<Any> =
                                    mutableListOf<Any>().apply {
                                        reader.readArray { this += elementSerde.read(reader) }
                                    }

                                override fun write(writer: BsonWriter, value: List<Any>) =
                                    writer.writeArray(value) { elementSerde.write(writer, it) }
                            }
                        }
                    } ?: EmptyListSerde
                ) as Serde<T>

            else -> getSerde(obj::class.java as Class<T>)
        }

    inline fun <reified T : Any> fromBson(bson: ByteArray): T = getSerde(T::class.java).fromBson(bson)
}
