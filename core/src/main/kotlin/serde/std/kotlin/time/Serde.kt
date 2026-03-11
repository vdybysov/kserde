package serde.std.kotlin.time

import kotlin.time.Duration
import kotlin.time.Instant
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import serde.Serde
import serde.ext.isJson

object DurationSerde : Serde<Duration> {
    override fun read(reader: BsonReader): Duration = Duration.parse(reader.readString())
    override fun write(writer: BsonWriter, value: Duration) = writer.writeString(value.toString())
}

object InstantSerde : Serde<Instant> {
    override fun read(reader: BsonReader): Instant = when (reader.currentBsonType) {
        BsonType.STRING -> try {
            Instant.parse(reader.readString())
        } catch (e: Throwable) {
            error(e)
        }
        BsonType.DATE_TIME -> Instant.fromEpochMilliseconds(reader.readDateTime())
        else -> Instant.fromEpochMilliseconds(reader.readInt64())
    }

    override fun write(writer: BsonWriter, value: Instant) =
        if (writer.isJson()) writer.writeString(value.toString())
        else writer.writeDateTime(value.toEpochMilliseconds())
}