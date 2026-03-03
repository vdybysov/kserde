val ktorVersion: String by project
val kotestVersion: String by project

dependencies {
    ksp(project(":processor"))
    implementation(project(":core"))
    implementation(project(":annotations"))
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}