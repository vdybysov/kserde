val kotlinxDatetimeVersion: String by project
val bsonVersion: String by project

dependencies {
    implementation(project(":annotations"))
    api("org.mongodb:bson:$bsonVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
}