import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

/**
 * The Firebase project identifiers, looked up the same way the Android app does it: a Gradle
 * property (-Pinventoria.FIREBASE_WEB_API_KEY=...) wins, then an environment variable (what CI
 * sets from secrets), then the gitignored .env at the repo root.
 */
val envFile = rootProject.file(".env")
val dotEnv = Properties().apply { if (envFile.exists()) envFile.reader().use { load(it) } }
fun config(name: String): String =
    (findProperty("inventoria.$name") as String?)
        ?: System.getenv(name)
        ?: dotEnv.getProperty(name)
        ?: ""

val generatedConfigDir = layout.buildDirectory.dir("generated/webConfig/kotlin")
val generateWebConfig by tasks.registering {
    val values = mapOf(
        "FIREBASE_WEB_API_KEY" to config("FIREBASE_WEB_API_KEY"),
        "FIREBASE_DATABASE_URL" to config("FIREBASE_DATABASE_URL"),
        "DEFAULT_WEB_CLIENT_ID" to config("DEFAULT_WEB_CLIENT_ID")
    )
    inputs.properties(values)
    outputs.dir(generatedConfigDir)
    doLast {
        val file = generatedConfigDir.get().file("com/inventoria/web/WebConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(buildString {
            appendLine("package com.inventoria.web")
            appendLine()
            appendLine("// Generated from .env / environment by :webApp:generateWebConfig. Do not edit.")
            appendLine("internal object WebConfig {")
            values.forEach { (key, value) ->
                appendLine("    const val $key = \"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            }
            appendLine("}")
        })
    }
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("inventoria")
        browser {
            commonWebpackConfig {
                outputFileName = "inventoria.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        wasmJsMain {
            kotlin.srcDir(generateWebConfig)
            dependencies {
                implementation(project(":shared"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.compose.material.icons.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.js)
            }
        }
    }
}
