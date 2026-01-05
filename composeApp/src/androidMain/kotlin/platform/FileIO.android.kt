package platform

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmStatic

actual object FileIO {
    private fun appFilesDir(): File {
        return AppContextHolder.context.filesDir
    }

    actual fun writeText(path: String, text: String) {
        val f = File(appFilesDir(), path)
        f.parentFile?.mkdirs()
        f.writeText(text)
    }

    actual fun readText(path: String): String {
        val f = File(appFilesDir(), path)
        return f.readText()
    }

    actual fun saveAttachment(id: String, bytes: ByteArray): String {
        val filename = "attachments/${id}.jpg"
        val f = File(appFilesDir(), filename)
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { it.write(bytes) }
        return filename
    }

    actual fun readAttachment(filename: String): ByteArray? {
        val f = File(appFilesDir(), filename)
        return if (f.exists()) f.readBytes() else null
    }
}

