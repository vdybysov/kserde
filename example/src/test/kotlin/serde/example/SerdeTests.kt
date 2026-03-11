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

        should("apply custom serde on Java record (merge annotations from param, accessor, property)") {
            val input = listOf(42, 99)
            val record = JavaRecordWithCustomSerde(input)

            val serde = registry.getSerde(JavaRecordWithCustomSerde::class.java)
            val json = serde.toJson(record)
            val restored = serde.fromJson(json)

            // KSP may put @Serde on param, accessor, or record component — processor merges all sources
            json shouldBe """{"ids": "42,99"}"""
            restored.ids() shouldBe input
        }

        should("apply custom serde on Java record Map (record component annotation)") {
            val input = linkedMapOf("a" to "1", "b" to "2")
            val record = JavaRecordWithMapSerde(input)

            val serde = registry.getSerde(JavaRecordWithMapSerde::class.java)
            val json = serde.toJson(record)
            val restored = serde.fromJson(json)

            // CustomMapSerde stores as "k=v,k=v"
            json shouldBe """{"steps": "a=1,b=2"}"""
            restored.steps() shouldBe input
        }

        should("apply custom serde on Java record Map with interface (Sequence-like)") {
            val input = linkedMapOf("x" to "1")
            val record = SequenceLikeRecord(input)

            val serde = registry.getSerde(SequenceLikeRecord::class.java)
            val json = serde.toJson(record)
            val restored = serde.fromJson(json)

            json shouldBe """{"steps": "x=1"}"""
            restored.steps() shouldBe input
        }
    }

    context("Constructor argument order") {
        should("pass constructor args in parameter order (not property order)") {
            val obj = JavaClassWithReorderedConstructor("a", "b", "c")

            val serde = registry.getSerde(JavaClassWithReorderedConstructor::class.java)
            val json = serde.toJson(obj)
            val restored = serde.fromJson(json)

            restored.parentA shouldBe "a"
            restored.parentB shouldBe "b"
            restored.childProp shouldBe "c"
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

    context("@PropertyIgnore") {
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

    context("Nullable fields") {
        should("write nullable String (Kotlin data class)") {
            val obj = NullableFields(required = "a", optional = "b", secret = "x")
            val serde = registry.getSerde(NullableFields::class.java)
            val json = serde.toJson(obj)
            val bson = serde.toBson(obj)
            val fromJson = serde.fromJson(json)
            val fromBson = serde.fromBson(bson)

            fromJson.required shouldBe "a"
            fromJson.optional shouldBe "b"
            fromJson.secret shouldBe "x"
            fromBson.required shouldBe "a"
            fromBson.optional shouldBe "b"
            fromBson.secret shouldBe "x"
        }

        should("write nullable field named 'value' (no shadowing of parameter)") {
            val obj = WithValueField(value = "v1", other = "v2")
            val serde = registry.getSerde(WithValueField::class.java)
            val json = serde.toJson(obj)
            val fromJson = serde.fromJson(json)
            fromJson.value shouldBe "v1"
            fromJson.other shouldBe "v2"
        }

        should("write nullable String on Java bean (local var for smart-cast)") {
            val obj = NullableFieldsBean().apply {
                setRequired("a")
                setOptional("b")
                setSecret("x")
            }
            val serde = registry.getSerde(NullableFieldsBean::class.java)
            val json = serde.toJson(obj)
            val fromJson = serde.fromJson(json)

            fromJson.required shouldBe "a"
            fromJson.optional shouldBe "b"
            fromJson.secret shouldBe "x"
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
