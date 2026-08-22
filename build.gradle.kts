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
    // Ktor 3.5.0's BOM lands Netty on 4.2.13.Final, which carries 19 advisories across
    // netty-codec-http (9), netty-codec-http2 (5), netty-handler (3), netty-codec-compression,
    // and the epoll/kqueue native transports. 4.2.17.Final is the first release clear of all of
    // them (CVE-2026-59903 alone needs 4.2.17). Drop this pin once Ktor's BOM catches up.
    implementation(platform("io.netty:netty-bom:4.2.17.Final"))

    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-jackson")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.1")
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
    implementation("ch.qos.logback:logback-classic:1.5.38")
    implementation(kotlin("reflect")) // runtime reflection for /tour/reflection

    // Persistence for /notes — Exposed DSL over SQLite-on-disk (see notes/NotesDatabase.kt). DSL,
    // not DAO: NoteRepository maps ResultRow <-> the Note data class by hand, so Note + its Jackson
    // runtime JSON / @Serializable OpenAPI schema stay untouched. createdAt is stored as epoch
    // millis (a long column), NOT exposed-java-time's timestamp() — that column type shifts the
    // Instant by the local UTC offset on SQLite round-trips. sqlite-jdbc bundles the Linux/aarch64
    // native lib for the arm64-only Pi image (node5) plus Mac/aarch64 for local + node0 builds.
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")

    // QR codes for the /signage TV view — a note's link is unusable on a display nobody can tap,
    // so it's rendered as a scannable code instead (signage/SignageQr.kt). zxing:core ONLY, not
    // zxing:javase: SignageQr walks the BitMatrix into an inline <svg> by hand, so there's no AWT,
    // no ImageIO, no temp files, and no second HTTP fetch from the TV.
    implementation("com.google.zxing:core:3.5.4")

    // Test-scope only (never shipped): ktor-server-test-host drags in ktor-client-apache5, whose
    // transitive Apache HttpComponents 5.5.1/5.3.6 carry CVE-2026-64607 / CVE-2026-54399 /
    // CVE-2026-54428. This single pin is enough — httpclient5 5.6.4 pulls httpcore5 and
    // httpcore5-h2 5.4.3 with it, so pinning those two separately was verified redundant.
    // Stays inside the 5.x line; drop once Ktor's BOM catches up.
    testImplementation("org.apache.httpcomponents.client5:httpclient5:5.6.4")

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
