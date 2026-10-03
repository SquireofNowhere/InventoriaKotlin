plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * The Inventoria MCP server: a headless stdio process that lets an MCP client (Claude Code, Claude
 * Desktop, ...) read and edit a vault through the same Firebase Realtime Database the phone and the
 * web app sync with. It builds on :shared for the row models and the REST client, so it never
 * touches the phone's local Room database -- the cloud is the source of truth and the phone merges
 * the server's writes on its next sync, exactly as it does for the web app.
 *
 * Build the runnable jar (works without an Android SDK):
 *   ./gradlew -Pinventoria.webOnly=true :mcpServer:fatJar
 */
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.cio)

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.test {
    useJUnitPlatform()
}

// One self-contained jar so an MCP client config is just `java -jar inventoria-mcp-all.jar`.
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles the server and all its dependencies into one runnable jar."
    archiveBaseName.set("inventoria-mcp")
    archiveClassifier.set("all")
    manifest { attributes["Main-Class"] = "com.inventoria.mcp.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}
