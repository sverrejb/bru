package works.bru

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val FILES_DIR = "files"

internal fun safeName(name: String): String =
    name.substringAfterLast('/').substringAfterLast('\\').trim()
        .takeUnless { it.isEmpty() || it == "." || it == ".." } ?: "file"

internal suspend fun saveDownload(context: Context, name: String, mime: String, body: Body): Uri =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloads(context, name, mime, body)
        } else {
            saveToCache(context, name, body)
        }
    }

private suspend fun saveToDownloads(context: Context, name: String, mime: String, body: Body): Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, mime)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("could not create download")
    try {
        val out = resolver.openOutputStream(uri) ?: throw IOException("could not open download")
        out.use { body(it) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return uri
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
}

private suspend fun saveToCache(context: Context, name: String, body: Body): Uri {
    val file = freshCacheFile(context, FILES_DIR, name)
    file.outputStream().use { body(it) }
    return cacheUri(context, file)
}
