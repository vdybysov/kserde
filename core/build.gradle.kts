val bsonVersion: String by project

dependencies {
    implementation(project(":annotations"))
    api("org.mongodb:bson:$bsonVersion")
}