plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("io.ktor.plugin") version "3.5.0"
    application
}

group = "dev.michaellamb"
version = "0.1.0"

application {
    mainClass.set("dev.michaellamb.tutorial.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-jackson")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-html-builder")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-swagger") // serves Swagger UI at /swagger
    implementation("io.ktor:ktor-server-routing-openapi") // OpenApiDoc + routing-tree spec source
    // @Serializable annotations are read ONLY by the OpenAPI schema generator (kotlinx-based), and
    // plugins/OpenApi.kt uses the kotlinx JSON tree to post-process the generated spec. Runtime JSON
    // I/O still goes through Jackson (see plugins/Serialization.kt). Version is aligned with the
    // kotlinx-serialization brought in transitively by ktor-openapi-schema.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation(kotlin("reflect")) // runtime reflection for /tour/reflection

    // Persistence for /notes — Exposed DSL over SQLite-on-disk (see notes/NotesDatabase.kt). DSL,
    // not DAO: NoteRepository maps ResultRow <-> the Note data class by hand, so Note + its Jackson
    // runtime JSON / @Serializable OpenAPI schema stay untouched. createdAt is stored as epoch
    // millis (a long column), NOT exposed-java-time's timestamp() — that column type shifts the
    // Instant by the local UTC offset on SQLite round-trips. sqlite-jdbc bundles the Linux/aarch64
    // native lib for the arm64-only Pi image (node5) plus Mac/aarch64 for local + node0 builds.
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    implementation("org.xerial:sqlite-jdbc:3.50.1.0")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation("io.ktor:ktor-client-mock")
    testImplementation(kotlin("test"))
}

// Ktor's OpenAPI compiler plugin: generate the spec from the routing tree at build time,
// inferring request/response/param schemas from call.receive/call.respond/call.parameters.
ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("version.properties") {
        expand(mapOf("version" to project.version.toString()))
    }
}
