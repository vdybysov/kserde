package serde.std.java.math

import java.math.BigDecimal
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.types.Decimal128
import serde.Serde
import serde.ext.isJson

object BigDecimalSerde : Serde<BigDecimal> {
    override fun read(reader: BsonReader) = when (reader.currentBsonType) {
        BsonType.STRING -> BigDecimal(reader.readString())
        BsonType.INT64 -> BigDecimal.valueOf(reader.readInt64())
        BsonType.INT32 -> BigDecimal.valueOf(reader.readInt32().toLong())
        BsonType.DOUBLE -> BigDecimal.valueOf(reader.readDouble())
        BsonType.DECIMAL128 -> reader.readDecimal128().bigDecimalValue()
        else -> throw IllegalArgumentException(
            "Cannot read BigDecimal from BSON type: ${reader.currentBsonType}"
        )
    }

    override fun write(writer: BsonWriter, value: BigDecimal) =
        if (writer.isJson()) writer.writeDouble(value.toDouble())
        else writer.writeDecimal128(Decimal128(value))
}