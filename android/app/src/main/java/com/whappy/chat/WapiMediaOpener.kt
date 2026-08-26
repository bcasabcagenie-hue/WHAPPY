package com.whappy.chat

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

/** Opens a WAPI attachment from an app-private cached copy, never as a public Firebase URL. */
object WapiMediaOpener {
    suspend fun open(context: Context, remoteUrl: String, displayName: String, fallbackMime: String): Result<Unit> = runCatching {
        require(remoteUrl.startsWith("https://firebasestorage.googleapis.com/")) { "media-source" }
        val safeName = displayName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(90)
            .ifBlank { "media-wapi" }
        val extension = safeName.substringAfterLast('.', "").lowercase()
        val cacheKey = WapiMediaStore.keyFor(remoteUrl)
        val target = File(WapiMediaStore.cacheDirectory(context), "$cacheKey-${safeName}")
        if (!target.isFile || target.length() == 0L) {
            val temporary = File(target.parentFile, ".${target.name}.part")
            FirebaseStorage.getInstance().getReferenceFromUrl(remoteUrl).getFile(temporary).await()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            WapiMediaStore.trimCache(context)
        }
        target.setLastModified(System.currentTimeMillis())
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty().ifBlank { fallbackMime }
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
