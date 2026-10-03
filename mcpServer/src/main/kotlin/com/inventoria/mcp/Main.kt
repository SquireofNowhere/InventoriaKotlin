package com.inventoria.mcp

import com.inventoria.shared.model.nowMillis
import com.inventoria.shared.remote.AuthManager
import com.inventoria.shared.remote.FirebaseAuthRest
import com.inventoria.shared.remote.FirebaseConfig
import com.inventoria.shared.remote.InventoriaJson
import com.inventoria.shared.remote.RealtimeDatabaseRest
import com.inventoria.shared.remote.SessionStore
import com.inventoria.shared.remote.installInventoriaJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.BufferedReader
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import kotlin.system.exitProcess

private const val VERSION = "0.1.0"

private const val INSTRUCTIONS = """
This server reads and edits one Inventoria vault: inventory items (which can nest inside container items), collections of items, todos (which can nest), time-tracking task segments, Task Types, and schedule blocks.

Start with vault_summary. Changes are written to the cloud database and reach the phone and web app the next time they sync, usually within seconds. Deletes are soft: nothing is erased, and restore brings it back. Dates are YYYY-MM-DD and times HH:MM, in the time zone vault_summary reports. Item and collection ids are numbers; todo, task, task type and schedule block ids are text.

Prefer the specific tools (move_item, set_equipped, set_todo_state ...) over editing fields, because they keep related rows consistent the way the app does. Ask the user before deleting several things at once. Running time-trackers are live on a device and cannot be started, stopped or edited from here.
"""

/** Where the server keeps its sign-in and which vault it joined. Override with INVENTORIA_MCP_HOME. */
private val home: Path = System.getenv("INVENTORIA_MCP_HOME")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
    ?: Paths.get(System.getProperty("user.home"), ".inventoria-mcp")

private val sessionFile = home.resolve("session.json")
private val vaultFile = home.resolve("vault.json")
private val configFile = home.resolve("config.json")

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

private fun writePrivate(path: Path, text: String) {
    Files.createDirectories(path.parent)
    Files.writeString(path, text)
    // Holds a refresh token. Windows has no POSIX permissions, where the user profile already guards it.
    try {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
    } catch (e: UnsupportedOperationException) {
    }
}

private class FileSessionStore(private val file: Path) : SessionStore {
    override fun load(): String? = runCatching { Files.readString(file) }.getOrNull()?.takeIf { it.isNotBlank() }

    override fun save(value: String?) {
        if (value == null) Files.deleteIfExists(file) else writePrivate(file, value)
    }
}

/**
 * The Firebase project identifiers. None are secrets (SECURITY.md), and they are looked up the way
 * the Gradle builds do it: an environment variable, then this server's config.json, then a .env in
 * the working directory -- so running `join` from the repo root picks up the repo's own .env.
 */
private object ProjectConfig {
    private fun readFlatJson(path: Path): Map<String, String> = runCatching {
        (InventoriaJson.parseToJsonElement(Files.readString(path)) as JsonObject)
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    private fun readDotEnv(path: Path): Map<String, String> = runCatching {
        Files.readAllLines(path)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
            .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim().trim('"', '\'') }
    }.getOrDefault(emptyMap())

    private val saved = readFlatJson(configFile)
    private val dotEnv = readDotEnv(Paths.get(".env"))

    private fun lookup(name: String): String? =
        (System.getenv(name)?.takeIf { it.isNotBlank() } ?: saved[name] ?: dotEnv[name])?.takeIf { it.isNotBlank() }

    fun load(): FirebaseConfig {
        val apiKey = lookup("FIREBASE_WEB_API_KEY")
        val databaseUrl = lookup("FIREBASE_DATABASE_URL")
        if (apiKey == null || databaseUrl == null) {
            fail(
                "Missing Firebase settings. Set FIREBASE_WEB_API_KEY and FIREBASE_DATABASE_URL (the same values as in " +
                    "the app's .env), either as environment variables or in $configFile, or run this command from " +
                    "the repository root where .env lives."
            )
        }
        // Remember them so the server works from any directory afterwards.
        if (saved["FIREBASE_WEB_API_KEY"] != apiKey || saved["FIREBASE_DATABASE_URL"] != databaseUrl) {
            writePrivate(
                configFile,
                InventoriaJson.encodeToString(
                    JsonObject.serializer(),
                    obj("FIREBASE_WEB_API_KEY" to apiKey, "FIREBASE_DATABASE_URL" to databaseUrl)
                )
            )
        }
        return FirebaseConfig(apiKey = apiKey, databaseUrl = databaseUrl, googleWebClientId = lookup("DEFAULT_WEB_CLIENT_ID") ?: "")
    }
}

/** The HTTP client, sign-in and database access every command needs. */
private class Connection(config: FirebaseConfig) {
    val http = HttpClient(CIO) { installInventoriaJson() }
    val auth = AuthManager(FirebaseAuthRest(http, config), FileSessionStore(sessionFile))
    val db = RealtimeDatabaseRest(http, config) { force -> auth.idToken(force) }
}

private fun savedOwnerUid(): String? = runCatching {
    ((InventoriaJson.parseToJsonElement(Files.readString(vaultFile)) as JsonObject)["ownerUid"] as? JsonPrimitive)?.contentOrNull
}.getOrNull()

private fun usage(): Nothing {
    System.err.println(
        """
        Inventoria MCP server $VERSION

          join <CODE>   One-time setup: join your vault with an invite code from the app
                        (Settings > Sync > Share, valid for 24 hours). Run it from the repo root so
                        the Firebase settings are picked up from .env.
          serve         Run the MCP server on stdio (the default; this is what an MCP client launches).
          status        Show which vault this server is joined to and whether it can read it.
          leave         Forget the vault locally. Also revoke this device in the app's sharing list.
        """.trimIndent()
    )
    exitProcess(2)
}

private suspend fun join(rawCode: String) {
    val connection = Connection(ProjectConfig.load())
    val code = rawCode.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
    if (code.isEmpty()) fail("That does not look like an invite code.")

    if (connection.auth.session.value == null) connection.auth.signInAnonymously()
    val me = connection.auth.session.value?.uid ?: fail("Could not sign in to Firebase.")

    val invite = connection.db.get("invites/$code") as? JsonObject
        ?: fail("No such invite code. Codes expire after 24 hours; generate a fresh one in the app.")
    val expiresAt = (invite["expiresAt"] as? JsonPrimitive)?.longOrNull ?: 0L
    if (expiresAt <= nowMillis()) fail("That invite code has expired. Generate a fresh one in the app.")
    val owner = (invite["uid"] as? JsonPrimitive)?.contentOrNull ?: fail("That invite code is malformed.")

    // The invite code is the value on purpose: the database rules check it points at the owner.
    connection.db.put("users/$owner/sharedWith/$me", JsonPrimitive(code))

    val vault = Vault(connection.db, owner)
    val itemCount = vault.items().size
    val todoCount = vault.todos().size
    writePrivate(vaultFile, InventoriaJson.encodeToString(JsonObject.serializer(), obj("ownerUid" to owner, "joinedAt" to nowMillis())))
    println("Joined the vault. It holds $itemCount items and $todoCount todos.")
    println("Add this server to your MCP client; see mcpServer/README.md for the config.")
}

private suspend fun status() {
    val owner = savedOwnerUid()
    if (owner == null) {
        println("Not joined to a vault yet. Run: join <CODE>")
        return
    }
    val connection = Connection(ProjectConfig.load())
    println("Vault owner uid: $owner")
    println("This server's uid: ${connection.auth.session.value?.uid ?: "(not signed in)"}")
    try {
        val vault = Vault(connection.db, owner)
        println("Can read the vault: ${vault.items().size} items, ${vault.todos().size} todos, ${vault.collections().size} collections.")
    } catch (e: Exception) {
        println("Cannot read the vault: ${e.message}")
        println("The owner may have revoked this device in the app; join again with a fresh code.")
    }
}

private suspend fun serve() {
    val owner = savedOwnerUid() ?: fail("Not set up yet. Run once: join <CODE> (invite code from the app).")
    val connection = Connection(ProjectConfig.load())
    if (connection.auth.session.value == null) fail("Signed out. Run: join <CODE> again.")

    val vault = Vault(connection.db, owner)
    val tools = readTools(vault) + inventoryTools(vault) + plannerTools(vault)
    val server = McpServer(tools, "inventoria", VERSION, INSTRUCTIONS.trim())

    // stdout is the protocol channel. Take the real one, and point System.out at stderr so a stray
    // println anywhere can never corrupt a message.
    val protocolOut = PrintStream(FileOutputStream(FileDescriptor.out), false, Charsets.UTF_8)
    System.setOut(System.err)
    log("Serving ${tools.size} tools for vault $owner")
    server.serve(BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8)), protocolOut)
}

fun main(args: Array<String>) {
    runBlocking {
        try {
            when (args.firstOrNull()) {
                null, "serve" -> serve()
                "join" -> join(args.getOrNull(1) ?: usage())
                "status" -> status()
                "leave" -> {
                    Files.deleteIfExists(sessionFile)
                    Files.deleteIfExists(vaultFile)
                    println("Forgotten locally. Revoke this device in the app's sharing list to cut its access.")
                }
                else -> usage()
            }
        } catch (e: ToolError) {
            fail(e.message ?: "Error")
        } catch (e: Exception) {
            fail("${e::class.simpleName}: ${e.message}")
        }
    }
}
