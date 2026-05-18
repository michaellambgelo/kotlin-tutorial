package dev.michaellamb.tutorial

import java.util.Properties

object BuildInfo {
    val version: String by lazy {
        BuildInfo::class.java.classLoader
            .getResourceAsStream("version.properties")
            ?.use { stream ->
                Properties().apply { load(stream) }.getProperty("version")
            } ?: "unknown"
    }
}
