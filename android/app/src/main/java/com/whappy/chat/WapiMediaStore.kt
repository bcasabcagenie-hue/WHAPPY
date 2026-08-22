package com.whappy.chat

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Local media policy for WAPI.
 *
 * Like a serious messenger, the app keeps three different classes of files:
 * - cache: thumbnails/remote previews that can always be downloaded again;
 * - media: files deliberately kept for offline playback or a user's own media;
 * - outbox: copies of attachments that must survive process death until upload.
 *
 * Cache is app-private, excluded from backups by Android and trimmed by LRU.
 * Durable files are never silently removed by cache cleanup.
 */
object WapiMediaStore {
    private const val CACHE_LIMIT_BYTES = 128L * 1024L * 1024L
    private const val MAX_CACHED_FILE_BYTES = 12L * 1024L * 1024L
    private const val OUTBOX_LIMIT_BYTES = 256L * 1024L * 1024L
    private const val MAX_OUTBOX_FILE_BYTES = 128L * 1024L * 1024L

    data class Usage(
        val cacheBytes: Long,
        val mediaBytes: Long,
        val outboxBytes: Long,
    ) {
        val totalBytes: Long get() = cacheBytes + mediaBytes + outboxBytes
    }

    fun cacheDirectory(context: Context): File = File(context.cacheDir, "wapi/cache").apply { mkdirs() }

    fun mediaDirectory(context: Context): File = File(context.filesDir, "wapi/media").apply { mkdirs() }

    fun outboxDirectory(context: Context): File = File(context.noBackupFilesDir, "wapi/outbox").apply { mkdirs() }

    fun readCache(context: Context, key: String, maxBytes: Long = MAX_CACHED_FILE_BYTES): ByteArray? {
        val file = File(cacheDirectory(context), "$key.bin")
        if (!file.isFile || file.length() !in 1..maxBytes) return null
        return runCatching {
            file.setLastModified(System.currentTimeMillis())
            file.readBytes()
        }.getOrNull()
    }

    fun writeCache(context: Context, key: String, bytes: ByteArray, maxBytes: Long = MAX_CACHED_FILE_BYTES) {
        if (bytes.isEmpty() || bytes.size > maxBytes) return
        val directory = cacheDirectory(context)
        val target = File(directory, "$key.bin")
        val temporary = File(directory, ".$key.tmp")
        runCatching {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                target.delete()
                temporary.renameTo(target)
            }
            trimCache(context)
        }.also { temporary.delete() }
    }

    fun keyFor(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(Locale.ROOT, it) }

    /** Copies a picked attachment so its content survives a provider revocation or process death. */
    fun copyToOutbox(context: Context, uri: Uri, kind: String, name: String): File? {
        val safeKind = kind.filter { it.isLetterOrDigit() }.take(12).ifBlank { "file" }
        val safeName = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { safeKind }
        val outbox = outboxDirectory(context)
        val advertisedSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
        if (advertisedSize > MAX_OUTBOX_FILE_BYTES || (advertisedSize > 0L && directorySize(outbox) + advertisedSize > OUTBOX_LIMIT_BYTES)) {
            return null
        }
        val target = File(outboxDirectory(context), "${System.currentTimeMillis()}-$safeName")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= MAX_OUTBOX_FILE_BYTES) { "attachment-too-large" }
                        output.write(buffer, 0, count)
                    }
                }
            }
                ?: error("attachment-unreadable")
            require(directorySize(outbox) <= OUTBOX_LIMIT_BYTES) { "outbox-full" }
            target
        }.getOrElse {
            target.delete()
            null
        }
    }

    fun trimCache(context: Context, limitBytes: Long = CACHE_LIMIT_BYTES) {
        // Remove the pre-1.8.1 image cache once; it was rebuildable and unbounded.
        File(context.cacheDir, "wapi_image_cache").takeIf(File::exists)?.deleteRecursively()
        val directory = cacheDirectory(context)
        var total = directory.walkTopDown().filter(File::isFile).sumOf(File::length)
        if (total <= limitBytes) return
        directory.walkTopDown()
            .filter(File::isFile)
            .sortedBy(File::lastModified)
            .forEach { file ->
                if (total <= limitBytes) return@forEach
                val size = file.length()
                if (file.delete()) total -= size
            }
    }

    fun clearRebuildableCache(context: Context) {
        cacheDirectory(context).deleteRecursively()
        cacheDirectory(context).mkdirs()
    }

    fun usage(context: Context): Usage = Usage(
        cacheBytes = directorySize(cacheDirectory(context)),
        mediaBytes = directorySize(mediaDirectory(context)),
        outboxBytes = directorySize(outboxDirectory(context)),
    )

    private fun directorySize(directory: File): Long = directory.walkTopDown().filter(File::isFile).sumOf(File::length)
}
