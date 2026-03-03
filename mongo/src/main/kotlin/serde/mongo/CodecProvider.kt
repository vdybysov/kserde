package serde.mongo

import java.util.concurrent.ConcurrentHashMap
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistry
import serde.SerdeRegistry

val SerdeRegistry.codecProvider: CodecProvider
    get() = object : CodecProvider {
        val codecs = ConcurrentHashMap<Class<*>, Codec<*>?>()

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> get(type: Class<T>, registry: CodecRegistry): Codec<T>? =
            codecs.computeIfAbsent(type) {
                runCatching { getSerde(type) }
                    .getOrNull()
                    ?.let { serde ->
                        object : Codec<T> {
                            override fun encode(writer: BsonWriter, value: T, ctx: EncoderContext) =
                                serde.write(writer, value)

                            override fun getEncoderClass() = type

                            override fun decode(reader: BsonReader, ctx: DecoderContext) =
                                serde.read(reader)
                        }
                    }
            } as Codec<T>?
    }
