package serde.example

import org.bson.BsonReader
import org.bson.BsonWriter
import serde.annotation.PropertyIgnore
import serde.annotation.PropertyName
import serde.annotation.Serde
import serde.annotation.SubTypes

// =============================================================================
// 1. Basic data class with @PropertyName (different BSON vs JSON field names)
// =============================================================================

@Serde
data class UserProfile(
    val id: String,
    val displayName: String,
    @param:PropertyName(bson = "tags_bson", json = "tags") val tags: Set<String>,
    val preferences: UserPreferences,
    val role: UserRole,
    val metadata: Map<UserRole, String>?
)

enum class UserRole {
    GUEST, USER, ADMIN
}

// =============================================================================
// 2. Custom serde — store List<Int> as comma-separated string "1,2,3"
// =============================================================================

object CustomIdsSerde : serde.Serde<List<Int>> {
    override fun read(reader: BsonReader): List<Int> =
        reader.readString().takeIf { it.isNotEmpty() }?.split(",")?.map { it.toInt() }?.toList() ?: emptyList()

    override fun write(writer: BsonWriter, value: List<Int>) =
        writer.writeString(value.joinToString(","))
}

@Serde
data class UserPreferences(@param:Serde(with = CustomIdsSerde::class) val favoriteIds: List<Int>)

// =============================================================================
// 3. Polymorphism with @SubTypes (discriminator-based deserialization)
// =============================================================================

@Serde
@SubTypes(
    propertyName = "kind",
    types = [
        SubTypes.Type(type = TextNotification::class, name = "text"),
        SubTypes.Type(type = ImageNotification::class, name = "image"),
        SubTypes.Type(type = SystemNotification::class, name = "system"),
    ]
)
interface Notification {
    val kind: String
}

@Serde
data class TextNotification(val message: String) : Notification {
    override val kind = "text"
}

@Serde
class ImageNotification(val imageUrl: String) : Notification {
    override val kind = "image"
}

@Serde
class SystemNotification(val code: String) : Notification {
    override val kind = "system"
}

// =============================================================================
// 4. @PropertyIgnore — exclude sensitive or internal fields from serialization
// =============================================================================

@Serde
data class ApiResponse(
    val requestId: String,
    val payload: String,
    @param:PropertyIgnore val internalTraceId: String
)

// =============================================================================
// 5. Nullable fields — write must use !! for compiler (no smart-cast on property access)
// =============================================================================

@Serde
data class NullableFields(
    val required: String,
    val optional: String?,
    @param:PropertyName(bson = "secret_bson", json = "secret") val secret: String?
)

@Serde
data class WithValueField(
    val value: String?,
    val other: String?
)

// =============================================================================
// 6. Standard library types (Date, BigDecimal, etc.)
// =============================================================================

@Serde
data class TimestampedRecord(
    val createdAt: java.time.Instant,
    val updatedAt: java.util.Date,
    val amount: java.math.BigDecimal
)
