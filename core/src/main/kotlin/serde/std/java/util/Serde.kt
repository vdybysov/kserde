package serde.std.java.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Date
import java.util.TreeSet
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import serde.Serde
import serde.ext.isJson
import serde.ext.readArray
import serde.ext.writeArray

private val ISO_8601: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
    .optionalStart()
    .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
    .optionalEnd()
    .appendPattern("Z")
    .toFormatter()
    .withZone(ZoneId.systemDefault())

object DateSerde : Serde<Date> {
    override fun read(reader: BsonReader): Date = when (reader.currentBsonType) {
        BsonType.STRING -> reader.readString().let { str ->
            runCatching {
                Date.from(Instant.parse(str))
            }.recoverCatching {
                Date.from(Instant.from(ISO_8601.parse(str)))
            }.getOrThrow()
        }

        BsonType.DATE_TIME -> Date(reader.readDateTime())
        else -> Date(reader.readInt64())
    }

    override fun write(writer: BsonWriter, value: Date) =
        when {
            writer.isJson() -> writer.writeString(ISO_8601.format(value.toInstant()))
            else -> writer.writeDateTime(value.time)
        }

}

object TreeSetSerde {
    fun <T> read(reader: BsonReader, readElement: () -> T): TreeSet<T> =
        sortedSetOf<T>().apply {
            reader.readArray { this += readElement() }
        }

    fun <T> write(writer: BsonWriter, value: Set<T>, writeElement: (T) -> Unit) {
        writer.writeArray(value, writeElement)
    }
}