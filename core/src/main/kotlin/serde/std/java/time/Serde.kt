package serde.std.java.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import serde.Serde
import serde.ext.isJson

private val ISO_8601: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
    .optionalStart()
    .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
    .optionalEnd()
    .appendPattern("Z")
    .toFormatter()

object DurationSerde : Serde<Duration> {
    override fun read(reader: BsonReader): Duration = Duration.parse(reader.readString())
    override fun write(writer: BsonWriter, value: Duration) {
        val days = value.toDays()
        if (days > 0 && value.toHoursPart() == 0 &&
            value.toMinutesPart() == 0 &&
            value.toSecondsPart() == 0 &&
            value.toMillisPart() == 0 &&
            value.toNanosPart() == 0
        ) {
            writer.writeString("P${days}D")
        } else {
            writer.writeString(value.toString())
        }
    }
}

object PeriodSerde : Serde<Period> {
    override fun read(reader: BsonReader): Period = Period.parse(reader.readString())
    override fun write(writer: BsonWriter, value: Period) = writer.writeString(value.toString())
}

object InstantSerde : Serde<Instant> {
    override fun read(reader: BsonReader): Instant = when (reader.currentBsonType) {
        BsonType.STRING -> reader.readString().let { str ->
            runCatching {
                Instant.parse(str)
            }.recoverCatching {
                java.time.Instant.from(ISO_8601.parse(str))
            }.getOrThrow()
        }

        BsonType.DATE_TIME -> Instant.ofEpochMilli(reader.readDateTime())
        else -> Instant.ofEpochMilli(reader.readInt64())
    }

    override fun write(writer: BsonWriter, value: Instant) =
        when {
            writer.isJson() -> writer.writeString(value.toString())
            else -> writer.writeDateTime(value.toEpochMilli())
        }
}

object LocalDateSerde : Serde<LocalDate> {
    override fun read(reader: BsonReader): LocalDate = LocalDate.parse(reader.readString())
    override fun write(writer: BsonWriter, value: LocalDate) = writer.writeString(value.toString())
}