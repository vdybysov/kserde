package serde.std.kotlin.time

import kotlin.time.Duration
import org.bson.BsonReader
import org.bson.BsonWriter
import serde.Serde

object DurationSerde : Serde<Duration> {
    override fun read(reader: BsonReader): Duration = Duration.parse(reader.readString())
    override fun write(writer: BsonWriter, value: Duration) = writer.writeString(value.toString())
}