package serde.example

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.io.BasicOutputBuffer
import org.bson.json.JsonReader
import serde.SerdeRegistry
import serde.ext.readDocumentFieldAndReset
import serde.std.java.math.BigDecimalSerde
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Date

class SerdeTests : ShouldSpec({

    val registry = SerdeRegistry.Default

    context("Basic serialization") {
        should("roundtrip UserProfile via BSON and JSON") {
            val profile = UserProfile(
                id = "user-1",
                displayName = "Alice",
                tags = setOf("premium", "verified"),
                preferences = UserPreferences(favoriteIds = listOf(10, 20)),
                role = UserRole.ADMIN,
                metadata = mapOf(UserRole.USER to "note", UserRole.GUEST to "guest-note")
            )

            val serde = registry.getSerde(UserProfile::class.java)
            val bson = serde.toBson(profile)
            val json = serde.toJson(profile)

            val fromBson = registry.fromBson<UserProfile>(bson)
            fromBson.id shouldBe profile.id
            fromBson.displayName shouldBe profile.displayName
            fromBson.tags shouldBe profile.tags
            fromBson.preferences.favoriteIds shouldBe profile.preferences.favoriteIds
            fromBson.role shouldBe profile.role

            json shouldBe """{"id": "user-1", "displayName": "Alice", "tags": ["premium", "verified"], "preferences": {"favoriteIds": "10,20"}, "role": "ADMIN", "metadata": {"USER": "note", "GUEST": "guest-note"}}"""
        }

        should("use different field names for BSON vs JSON (@PropertyName)") {
            val preferences = UserPreferences(favoriteIds = listOf(3, 4))
            val json = registry.getSerde(UserPreferences::class.java).toJson(preferences)

            // CustomIdsSerde: stores List<Int> as comma-separated string
            json shouldBe """{"favoriteIds": "3,4"}"""
        }
    }

    context("Custom serde") {
        should("apply custom read/write logic via @Serde(with = ...)") {
            val input = listOf(100, 200)
            val preferences = UserPreferences(input)

            val serde = registry.getSerde(UserPreferences::class.java)
            val json = serde.toJson(preferences)
            val restored = serde.fromJson(json)

            // Stored as "100,200"; read back as [100, 200]
            restored.favoriteIds shouldBe input
        }
    }

    context("Polymorphism (@SubTypes)") {
        should("serialize and deserialize different notification types") {
            val text = TextNotification(message = "Hello!")
            val image = ImageNotification(imageUrl = "https://example.com/img.png")
            val system = SystemNotification(code = "SYS-001")

            val serde = registry.getSerde(Notification::class.java)

            val textJson = serde.toJson(text)
            val imageJson = serde.toJson(image)
            val systemJson = serde.toJson(system)

            textJson shouldBe """{"kind": "text", "message": "Hello!"}"""
            imageJson shouldBe """{"kind": "image", "imageUrl": "https://example.com/img.png"}"""
            systemJson shouldBe """{"kind": "system", "code": "SYS-001"}"""

            (serde.fromJson(textJson) as TextNotification).message shouldBe text.message
            (serde.fromJson(imageJson) as ImageNotification).imageUrl shouldBe image.imageUrl
            (serde.fromJson(systemJson) as SystemNotification).code shouldBe system.code
        }
    }

    context("@PropertyExclude") {
        should("exclude annotated fields from serialization") {
            val response = ApiResponse(
                requestId = "req-123",
                payload = "{\"ok\": true}",
                internalTraceId = "internal-secret-456"
            )

            val json = registry.getSerde(ApiResponse::class.java).toJson(response)

            json shouldBe """{"requestId": "req-123", "payload": "{\"ok\": true}"}"""
            json.contains("internalTraceId") shouldBe false // sensitive field excluded
        }
    }

    context("Standard library types") {
        should("roundtrip TimestampedRecord (Instant, Date, BigDecimal)") {
            val instant = Instant.parse("2024-01-15T10:30:00Z")
            val date = Date.from(instant)
            val amount = BigDecimal("99.99")

            val record = TimestampedRecord(instant, date, amount)
            val serde = registry.getSerde(TimestampedRecord::class.java)

            val fromBson = serde.fromBson(serde.toBson(record))
            val fromJson = serde.fromJson(serde.toJson(record))

            fromBson.createdAt shouldBe instant
            fromBson.updatedAt.time shouldBe date.time
            fromBson.amount shouldBe amount

            fromJson.createdAt shouldBe instant
            fromJson.updatedAt.time shouldBe date.time
            fromJson.amount shouldBe amount
        }
    }

    context("Reader resilience") {
        should("reset reader when readValue throws") {
            val json = """{"type": "A", "data": "value"}"""
            val reader = JsonReader(json)

            shouldThrow<RuntimeException> {
                reader.readDocumentFieldAndReset("type") {
                    throw RuntimeException("test exception")
                }
            }

            // After exception, reader is reset — we can read again
            val type = reader.readDocumentFieldAndReset("type") { readString() }
            type shouldBe "A"
        }
    }

    context("Error handling") {
        should("BigDecimal throws on unsupported BSON type") {
            val buffer = BasicOutputBuffer()
            BsonBinaryWriter(buffer).use { writer ->
                writer.writeStartDocument()
                writer.writeName("value")
                writer.writeStartDocument()
                writer.writeEndDocument()
                writer.writeEndDocument()
            }

            val reader = BsonBinaryReader(ByteBuffer.wrap(buffer.toByteArray()))
            reader.readStartDocument()
            reader.readName() shouldBe "value"

            val ex = shouldThrow<IllegalArgumentException> {
                BigDecimalSerde.read(reader)
            }
            ex.message shouldBe "Cannot read BigDecimal from BSON type: DOCUMENT"
        }
    }
})
