package works.bru

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val CLIP_DIR = "clip"

internal suspend fun writeClip(context: Context, mime: String, bytes: ByteArray): Uri =
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, CLIP_DIR).apply {
            mkdirs()
            listFiles()?.forEach(File::delete)
        }
        val file = File(dir, "${System.currentTimeMillis()}.${extensionOf(mime)}")
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.clip", file)
    }

internal fun extensionOf(mime: String): String = mime.substringAfter('/', "jpg").substringBefore('+')
