package serde.std.kotlin

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import serde.Serde
import serde.ext.readEnum
import serde.ext.writeEnum

object EnumSerde {
    inline fun <reified T : Enum<T>> read(reader: BsonReader): T = reader.readEnum<T>()
    fun write(writer: BsonWriter, value: Enum<*>) = writer.writeEnum(value)
}

object BooleanSerde : Serde<Boolean> {
    override fun read(reader: BsonReader): Boolean = reader.readBoolean()
    override fun write(writer: BsonWriter, value: Boolean) = writer.writeBoolean(value)
}

object StringSerde : Serde<String> {
    override fun read(reader: BsonReader): String = with(reader) {
        when(currentBsonType) {
            BsonType.INT32 -> readInt32().toString()
            BsonType.INT64 -> readInt64().toString()
            BsonType.DOUBLE -> readDouble().toString()
            else -> readString()
        }
    }
    override fun write(writer: BsonWriter, value: String) = writer.writeString(value)
}

object IntSerde : Serde<Int> {
    override fun read(reader: BsonReader): Int = when (reader.currentBsonType) {
        BsonType.STRING -> reader.readString().toInt()
        BsonType.INT64 -> reader.readInt64().toInt()
        BsonType.DOUBLE -> reader.readDouble().toInt()
        else -> reader.readInt32()
    }

    override fun write(writer: BsonWriter, value: Int) = writer.writeInt32(value)
}

object DoubleSerde : Serde<Double> {
    override fun read(reader: BsonReader): Double = when (reader.currentBsonType) {
        BsonType.STRING -> reader.readString().toDouble()
        BsonType.INT32 -> reader.readInt32().toDouble()
        BsonType.INT64 -> reader.readInt64().toDouble()
        BsonType.DOUBLE -> reader.readDouble()
        else -> reader.readDouble()
    }

    override fun write(writer: BsonWriter, value: Double) = writer.writeDouble(value)
}

object LongSerde : Serde<Long> {
    override fun read(reader: BsonReader): Long = when (reader.currentBsonType) {
        BsonType.STRING -> reader.readString().toLong()
        BsonType.INT32 -> reader.readInt32().toLong()
        BsonType.DOUBLE -> reader.readDouble().toLong()
        else -> reader.readInt64()
    }

    override fun write(writer: BsonWriter, value: Long) = writer.writeInt64(value)
}