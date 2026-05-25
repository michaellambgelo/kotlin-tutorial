plugins {
    kotlin("jvm") version "2.1.0"
    id("io.ktor.plugin") version "3.0.3"
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
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation(kotlin("reflect")) // runtime reflection for /tour/reflection

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("version.properties") {
        expand(mapOf("version" to project.version.toString()))
    }
}
