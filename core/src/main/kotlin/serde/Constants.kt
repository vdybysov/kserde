package serde

import org.bson.BsonReader
import org.bson.BsonWriter

object VarNames {
    const val READER = "reader"
    const val WRITER = "writer"
    const val VALUE = "value"
    const val OBJ = "obj"
    const val SERDE = "Serde"
}

object FnNames {
    const val READ = "read"
    const val WRITE = "write"
    const val WRITE_FIELDS = "writeFields"
}

object Types {
    val READER = BsonReader::class
    val WRITER = BsonWriter::class
}

object PackageNames {
    const val EXT = "serde.ext"
    const val STD = "serde.std"
}

object BsonReaderExtNames {
    const val READ_DOCUMENT_FIELD_AND_RESET = "readDocumentFieldAndReset"
    const val READ_STRING = "readString"
}