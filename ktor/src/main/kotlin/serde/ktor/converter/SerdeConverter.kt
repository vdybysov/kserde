package serde.ktor.converter

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.readRemaining
import kotlinx.io.readString
import serde.Serde
import serde.SerdeRegistry

class SerdeConverter(private val serdeRegistry: SerdeRegistry) : ContentConverter {

    @Suppress("UNCHECKED_CAST")
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? {
        if (value == null) return null
        return TextContent(
            text = getSerde(typeInfo).toJson(value),
            contentType = contentType.withCharset(charset),
        )
    }

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any? {
        val json = content.readRemaining().readString(charset)
        if (json.isEmpty()) return null
        return getSerde(typeInfo).fromJson(json)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getSerde(typeInfo: TypeInfo): Serde<Any> =
        serdeRegistry.getSerde(typeInfo.type.java) as Serde<Any>
}