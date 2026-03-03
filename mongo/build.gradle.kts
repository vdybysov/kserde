val bsonVersion: String by project

dependencies {
    implementation(project(":core"))
    implementation("org.mongodb:bson:$bsonVersion")
}
