package com.inventoria.mcp

import com.inventoria.shared.model.InventoryItem
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64

/** storage.rules refuses anything at or over this. */
private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

class SniffedImage(val contentType: String, val extension: String)

/**
 * What kind of picture [bytes] is, from its first bytes rather than its file name, or null when it
 * is not one Storage should take. Reading the bytes means a mislabelled or non-image file is refused
 * here instead of being uploaded under an image type.
 */
fun sniffImage(bytes: ByteArray): SniffedImage? {
    fun starts(vararg head: Int) = bytes.size >= head.size && head.indices.all { (bytes[it].toInt() and 0xFF) == head[it] }
    return when {
        starts(0xFF, 0xD8, 0xFF) -> SniffedImage("image/jpeg", "jpg")
        starts(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> SniffedImage("image/png", "png")
        starts(0x47, 0x49, 0x46, 0x38) -> SniffedImage("image/gif", "gif")
        // RIFF....WEBP
        starts(0x52, 0x49, 0x46, 0x46) && bytes.size >= 12 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" ->
            SniffedImage("image/webp", "webp")
        else -> null
    }
}

/**
 * Photo tools. [upload] puts image bytes in this server's own Storage folder and returns the
 * tokenized download URL (the phone does the same into its own folder; see storage.rules), or is
 * null when no storage bucket is configured.
 */
fun imageTools(vault: Vault, upload: (suspend (bytes: ByteArray, image: SniffedImage) -> String)?): List<Tool> = listOf(
    Tool(
        name = "add_item_image",
        description = "Attach a photo to an item. Give file_path (an image file on this machine: JPEG, PNG, GIF or WebP, " +
            "under 10 MB) or data_base64. It is uploaded to the vault's Storage and its URL added to the item's " +
            "imageUrls, so it shows on the phone at the next sync. make_profile sets it as the item's main picture; " +
            "otherwise the first image is the one shown.",
        inputSchema = schema(
            "item_id" to intP("Item id"),
            "file_path" to strP("Path of an image file on this machine"),
            "data_base64" to strP("The image itself, base64 encoded, instead of file_path"),
            "make_profile" to boolP("Also make it the item's profile picture"),
            required = listOf("item_id")
        ),
        readOnly = false
    ) { args ->
        val id = args.reqLong("item_id")
        vault.requireItem(id)
        val send = upload ?: throw ToolError(
            "No Storage bucket configured. Set FIREBASE_STORAGE_BUCKET (the same value as in the app's .env) and restart the server."
        )
        val bytes = when {
            args.has("file_path") && args.has("data_base64") -> throw ToolError("Send file_path or data_base64, not both")
            args.has("file_path") -> {
                val path = runCatching { Paths.get(args.reqStr("file_path")) }.getOrElse { throw ToolError("'file_path' is not a valid path") }
                if (!Files.isRegularFile(path)) throw ToolError("No file at ${path.toAbsolutePath()}")
                if (Files.size(path) >= MAX_IMAGE_BYTES) throw ToolError("That file is 10 MB or more; Storage will not take it")
                Files.readAllBytes(path)
            }
            args.has("data_base64") -> runCatching { Base64.getMimeDecoder().decode(args.reqStr("data_base64")) }
                .getOrElse { throw ToolError("'data_base64' is not valid base64") }
            else -> throw ToolError("Send file_path or data_base64")
        }
        if (bytes.size >= MAX_IMAGE_BYTES) throw ToolError("That image is 10 MB or more; Storage will not take it")
        val image = sniffImage(bytes) ?: throw ToolError("That is not a JPEG, PNG, GIF or WebP image")

        val url = send(bytes, image)
        val updated = vault.edit("items", id.toString(), InventoryItem.serializer()) { item ->
            item.copy(
                imageUrls = item.imageUrls + url,
                profilePictureUrl = if (args.bool("make_profile") == true) url else item.profilePictureUrl
            )
        }
        obj("item" to itemView(updated), "url" to url)
    },

    Tool(
        name = "remove_item_image",
        description = "Take a photo off an item by its URL (from the item's imageUrls). If it was the profile picture that " +
            "is cleared too. The file itself stays in Storage, since a photo taken on a phone lives in that phone's " +
            "folder, which this server cannot delete from.",
        inputSchema = schema("item_id" to intP("Item id"), "url" to strP("The image URL to remove"), required = listOf("item_id", "url")),
        readOnly = false,
        destructive = true
    ) { args ->
        val id = args.reqLong("item_id")
        val url = args.reqStr("url")
        val item = vault.requireItem(id)
        if (url !in item.imageUrls && url != item.profilePictureUrl) throw ToolError("Item $id does not have that image")
        val updated = vault.edit("items", id.toString(), InventoryItem.serializer()) {
            it.copy(
                imageUrls = it.imageUrls - url,
                profilePictureUrl = if (it.profilePictureUrl == url) null else it.profilePictureUrl
            )
        }
        obj("item" to itemView(updated))
    },

    Tool(
        name = "set_item_profile_picture",
        description = "Choose which of an item's photos is its main one (a URL from its imageUrls), or null to go back to " +
            "showing the first.",
        inputSchema = schema(
            "item_id" to intP("Item id"),
            "url" to strP("An image URL the item already has, or null to clear", nullable = true),
            required = listOf("item_id", "url")
        ),
        readOnly = false
    ) { args ->
        val id = args.reqLong("item_id")
        if (!args.has("url")) throw ToolError("'url' is required (use null to clear)")
        val url = args.str("url")
        val item = vault.requireItem(id)
        if (url != null && url !in item.imageUrls) throw ToolError("Item $id does not have that image; add it with add_item_image first")
        val updated = vault.edit("items", id.toString(), InventoryItem.serializer()) { it.copy(profilePictureUrl = url) }
        obj("item" to itemView(updated))
    }
)
