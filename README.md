# kserde

**Compile-time BSON/JSON serialization for Kotlin** using [KSP](https://kotlinlang.org/docs/ksp-overview.html). Generates type-safe, reflection-free serialization code for your data classes.



## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Annotations Reference](#annotations-reference)
- [Built-in Types](#built-in-types)
- [Integrations](#integrations)
- [Serde Interface](#serde-interface)
- [Generated Code](#generated-code)
- [Examples](#examples)
- [Project Structure](#project-structure)
- [Requirements](#requirements)

## Features

- **Compile-time code generation** — No reflection at runtime, generated `ObjectSerde` implementations
- **Dual format support** — BSON and JSON via MongoDB's `BsonReader`/`BsonWriter`
- **Polymorphism** — Sealed hierarchies with `@SubTypes` discriminator
- **Custom serdes** — Override generation with `@Serde(with = CustomSerde::class)`
- **Format-specific names** — Different property names for BSON vs JSON via `@PropertyName`
- **MongoDB integration** — `CodecProvider` for seamless MongoDB driver usage
- **Ktor integration** — `ContentConverter` for HTTP JSON (de)serialization

## Installation

### JitPack (recommended)

Add the JitPack repository and dependencies:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// build.gradle.kts
plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

dependencies {
    ksp("com.github.vdybysov.kserde:processor:0.0.1")
    implementation("com.github.vdybysov.kserde:core:0.0.1")
    implementation("com.github.vdybysov.kserde:annotations:0.0.1")

    // Optional: Ktor ContentConverter for JSON
    implementation("com.github.vdybysov.kserde:ktor:0.0.1")

    // Optional: MongoDB CodecProvider
    implementation("com.github.vdybysov.kserde:mongo:0.0.1")
}
```

Replace `0.0.1` with the [latest release](https://github.com/vdybysov/kserde/releases) or use `-SNAPSHOT` for the latest commit.

Generated code is placed in `build/generated/ksp/main/kotlin/` and is automatically included in the compilation classpath.

## Quick Start

### 1. Annotate your data class

```kotlin
import serde.annotation.Serde

@Serde
data class User(
    val id: String,
    val name: String,
    val email: String?,
    val createdAt: Instant
)
```

### 2. Generated Serde objects

KSP generates `UserSerde` (and other `*Serde` objects) in `build/generated/ksp/main/kotlin/`. Serdes are discovered by convention `{package}.{ClassName}Serde`. Use generated ones directly, or create your own in the matching package (e.g. for third-party types):

```kotlin
import your.package.UserSerde

val user = User("1", "Alice", "alice@example.com", Instant.now())

// Serialize
val json = UserSerde.toJson(user)
val bson = UserSerde.toBson(user)

// Deserialize
val fromJson = UserSerde.fromJson(json)
val fromBson = UserSerde.fromBson(bson)
```

### 3. SerdeRegistry (optional)

For dynamic lookup by class, use `SerdeRegistry`. It finds serdes by the same convention, so it picks up both KSP-generated and manually placed serdes:

```kotlin
import serde.SerdeRegistry

val registry = SerdeRegistry.Default
val serde = registry.getSerde(User::class.java)

val json = serde.toJson(user)
val restored = serde.fromJson(json)
```

### Complete Example

```kotlin
import org.bson.BsonReader
import org.bson.BsonWriter
import serde.annotation.PropertyName
import serde.annotation.Serde

enum class UserRole { GUEST, USER, ADMIN }

@Serde
data class UserProfile(
    val id: String,
    val displayName: String,
    @PropertyName(bson = "tags_bson", json = "tags") val tags: Set<String>,
    val preferences: UserPreferences,
    val role: UserRole
)

@Serde
data class UserPreferences(
    @Serde(with = CustomIdsSerde::class) val favoriteIds: List<Int>
)

// Custom format: store List<Int> as comma-separated string "1,2,3"
object CustomIdsSerde : serde.Serde<List<Int>> {
    override fun read(reader: BsonReader): List<Int> =
        reader.readString()
            .takeIf { it.isNotEmpty() }
            ?.split(",")
            ?.map { it.toInt() }
            ?.toList()
            ?: emptyList()

    override fun write(writer: BsonWriter, value: List<Int>) =
        writer.writeString(value.joinToString(","))
}

// Usage
val profile = UserProfile(
    "1", "Alice", setOf("a", "b"),
    UserPreferences(listOf(10, 20)),
    UserRole.ADMIN
)
val json = UserProfileSerde.toJson(profile)
val restored = UserProfileSerde.fromBson(UserProfileSerde.toBson(profile))
```

See the [example](example/) module for more: polymorphism (`@SubTypes`), `@PropertyIgnore`, standard types (Instant, Date, BigDecimal), and custom serdes.

## Annotations Reference

### @Serde

Marks a class for serialization. The KSP processor generates an `ObjectSerde` implementation.


| Parameter | Type        | Description                                             |
| --------- | ----------- | ------------------------------------------------------- |
| `with`    | `KClass<*>` | Custom serde class. If specified, no code is generated. |


```kotlin
@Serde
data class Simple(val x: Int)

@Serde(with = CustomUserSerde::class)
data class User(val id: String)

@Serde
@Mutable
class ComplexEntity { var id: String = ""; var name: String = "" }
```

### @Mutable

Use mutable deserialization (setters) instead of constructor. For classes without primary constructor. Place alongside `@Serde` on the class.

### @SubTypes

Defines polymorphic type hierarchy. Used on a parent interface/class; subtypes are resolved by a discriminator property.


| Parameter      | Type                   | Default  | Description                            |
| -------------- | ---------------------- | -------- | -------------------------------------- |
| `propertyName` | `String`               | `"type"` | Name of the discriminator field        |
| `types`        | `Array<SubTypes.Type>` | —        | List of subtype mappings               |
| `fallbackType` | `KClass<*>`            | —        | Fallback when discriminator is unknown |


```kotlin
@Serde
@SubTypes(
    propertyName = "kind",
    types = [
        SubTypes.Type(type = Dog::class, name = "dog"),
        SubTypes.Type(type = Cat::class, name = "cat"),
    ]
)
interface Animal {
    val kind: String
}

@Serde
data class Dog(val name: String) : Animal {
    override val kind = "dog"
}

@Serde
data class Cat(val lives: Int) : Animal {
    override val kind = "cat"
}
```

### @PropertyName

Custom property names for BSON and/or JSON. Useful when API contracts differ from internal naming.


| Parameter | Type     | Description                               |
| --------- | -------- | ----------------------------------------- |
| `bson`    | `String` | BSON field name (empty = use Kotlin name) |
| `json`    | `String` | JSON field name (empty = use Kotlin name) |


```kotlin
@Serde
data class ApiResponse(
    @PropertyName(json = "user_id") val userId: String,
    @PropertyName(bson = "_id", json = "id") val id: ObjectId
)
```

### @PropertyIgnore

Exclude property from serialization.


| Parameter | Type      | Default | Description     |
| --------- | --------- | ------- | --------------- |
| `bson`    | `Boolean` | `true`  | Exclude in BSON |
| `json`    | `Boolean` | `true`  | Exclude in JSON |


```kotlin
@Serde
data class ApiResponse(
    val requestId: String,
    val payload: String,
    @PropertyIgnore val internalTraceId: String  // excluded from BSON/JSON
)
```

### @ReadOnly / @WriteOnly

Property is read-only (deserialized but not serialized) or write-only (serialized but not deserialized).

```kotlin
@Serde
data class Document(
    val id: String,
    @ReadOnly val computedAt: Instant?,  // Read from storage, never write
    @WriteOnly val internalFlag: Boolean  // Write for debugging, never read
)
```

## Built-in Types

The following types have built-in serdes in `serde.std`:


| Package                        | Types                                                           |
| ------------------------------ | --------------------------------------------------------------- |
| `serde.std.kotlin`             | `Boolean`, `String`, `Int`, `Long`, `Double`, `Enum`            |
| `serde.std.kotlin.collections` | `List`, `MutableList`, `Set`, `MutableSet`, `Map`, `MutableMap` |
| `serde.std.kotlin.time`        | `kotlin.time.Duration`                                          |
| `serde.std.kotlinx.datetime`   | `kotlinx.datetime.Instant`                                      |
| `serde.std.java.time`          | `Instant`, `LocalDate`, `Duration`, `Period`                    |
| `serde.std.java.util`          | `Date`                                                          |
| `serde.std.java.math`          | `BigDecimal`                                                    |
| `serde.std.org.bson.types`     | `ObjectId`                                                      |


## Integrations

### Ktor ContentConverter

Use `SerdeConverter` for JSON (de)serialization in Ktor HTTP client or server:

```kotlin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import serde.ktor.converter.SerdeConverter
import serde.SerdeRegistry

// Ktor Client
HttpClient {
    install(ContentNegotiation) {
        register(
            ContentType.Application.Json,
            SerdeConverter(SerdeRegistry.Default)
        )
    }
}

// Ktor Server
install(ContentNegotiation) {
    register(
        ContentType.Application.Json,
        SerdeConverter(SerdeRegistry.Default)
    )
}
```

### MongoDB CodecProvider

Use the registry's `codecProvider` to register serdes with the MongoDB driver. Requires `mongo` module.

```kotlin
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.bson.codecs.configuration.CodecRegistries
import serde.SerdeRegistry
import serde.mongo.codecProvider

val settings = MongoClientSettings.builder()
    .applyConnectionString(ConnectionString("mongodb://localhost"))
    .codecRegistry(
        CodecRegistries.fromProviders(
            SerdeRegistry.Default.codecProvider,
            // ... other providers
        )
    )
    .build()

val client = MongoClients.create(settings)
val collection = client
    .getDatabase("mydb")
    .getCollection("users", User::class.java)
```

## Serde Interface

```kotlin
interface Serde<T : Any> {
    fun read(reader: BsonReader): T
    fun readList(reader: BsonReader): MutableList<T>
    fun write(writer: BsonWriter, value: T)
    fun writeList(writer: BsonWriter, coll: Iterable<T>)
    fun fromBson(bson: ByteArray): T
    fun fromJson(json: String): T
    fun toBson(obj: T): ByteArray
    fun toBsonDocument(obj: T): BsonDocument
    fun toJson(obj: T): String
    fun copy(obj: T): T
}
```

## Generated Code

For a class like:

```kotlin
@Serde
data class User(val id: String, val name: String)
```

The KSP processor generates `UserSerde` in `build/generated/ksp/main/kotlin/`:

```kotlin
public object UserSerde : ObjectSerde<User> {
    override fun read(reader: BsonReader): User {
        var id: String? = null
        var name: String? = null
        reader.readDocument {
            when (it) {
                "id" -> id = StringSerde.read(reader)
                "name" -> name = StringSerde.read(reader)
                else -> reader.skipValue()
            }
        }
        require(id != null) { "Parameter 'id' is required." }
        require(name != null) { "Parameter 'name' is required." }
        return User(id!!, name!!)
    }

    override fun writeFields(writer: BsonWriter, value: User) {
        writer.writeName("id")
        StringSerde.write(writer, value.id)
        writer.writeName("name")
        StringSerde.write(writer, value.name)
    }
}
```

The registry loads these generated objects by convention: `{package}.{ClassName}Serde`. For custom types (third-party, etc.) create `XxxSerde` in the package that matches `Xxx` — the registry will find it.

## Examples

The `example` module demonstrates all major features:


| Model                                | Demonstrates                                                            |
| ------------------------------------ | ----------------------------------------------------------------------- |
| `UserProfile`                        | Basic serialization, `@PropertyName`, nested objects, enum, `Map<K, V>` |
| `UserPreferences` + `CustomIdsSerde` | Custom serde via `@Serde(with = ...)`                                   |
| `Notification` (Text/Image/System)   | Polymorphism with `@SubTypes` discriminator                             |
| `ApiResponse`                        | `@PropertyIgnore` for sensitive/internal fields                         |
| `TimestampedRecord`                  | Standard types: `Instant`, `Date`, `BigDecimal`                         |


Run tests: `./gradlew :example:test`

## Project Structure

```
kserde/
├── annotations/    # @Serde, @Mutable, @SubTypes, @PropertyName, @PropertyIgnore, etc.
├── core/           # Serde interface, SerdeRegistry, built-in serdes
├── processor/      # KSP processor (generates ObjectSerde implementations)
├── mongo/          # MongoDB CodecProvider integration
├── ktor/           # Ktor ContentConverter
└── example/        # Usage examples and tests
```

## Requirements

- Kotlin 2.3+
- [KSP](https://kotlinlang.org/docs/ksp-overview.html)
- Java 25+
- MongoDB BSON 5.5+

## Publishing (for maintainers)

The library is published via [JitPack](https://jitpack.io/#vdybysov/kserde). To release a new version:

1. Update `version` in `gradle.properties`
2. Create a [GitHub Release](https://github.com/vdybysov/kserde/releases/new) with tag `v0.0.1` (match the version)
3. JitPack will automatically build and publish the artifacts

## Contributing

Contributions are welcome. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines and open an issue or submit a pull request.

## License

[MIT License](LICENSE)