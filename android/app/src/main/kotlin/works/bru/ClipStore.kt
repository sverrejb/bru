package works.bru

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

private const val CLIP_DIR = "clip"
private const val SEND_CAP = 5 * 1024 * 1024
// reasonable max ceiling so we don't use all the ram
private const val MAX_DIM = 2048
private const val JPEG_QUALITY = 80

internal suspend fun writeClip(context: Context, mime: String, bytes: ByteArray): Uri =
    withContext(Dispatchers.IO) {
        val file = freshCacheFile(context, CLIP_DIR, "${System.currentTimeMillis()}.${extensionOf(mime)}")
        file.writeBytes(bytes)
        cacheUri(context, file)
    }

internal fun freshCacheFile(context: Context, dir: String, name: String): File {
    val parent = File(context.cacheDir, dir).apply {
        mkdirs()
        listFiles()?.forEach(File::delete)
    }
    return File(parent, name)
}

internal fun cacheUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.clip", file)

internal fun extensionOf(mime: String): String = mime.substringAfter('/', "jpg").substringBefore('+')

internal suspend fun imageForSend(context: Context, uri: Uri): Pair<String, ByteArray>? =
    withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext null
        if (raw.size <= SEND_CAP) (context.contentResolver.getType(uri) ?: "image/jpeg") to raw
        else compress(raw)
    }

private fun compress(raw: ByteArray): Pair<String, ByteArray>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / MAX_DIM)
    }
    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, options) ?: return null
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    bitmap.recycle()
    return "image/jpeg" to out.toByteArray()
}
