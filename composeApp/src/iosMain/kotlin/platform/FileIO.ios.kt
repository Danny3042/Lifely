package platform

import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSFileManager
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual object FileIO {
    private fun appFilesDir(): String = NSHomeDirectory() + "/Documents"
    private fun fullPath(path: String): String = appFilesDir() + "/" + path

    private fun ensureParentDir(full: String) {
        val fm = NSFileManager.defaultManager
        val parent = full.substringBeforeLast('/')
        if (parent.isNotEmpty() && !fm.fileExistsAtPath(parent)) {
            fm.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
        }
    }

    actual fun writeText(path: String, text: String) {
        val full = fullPath(path)
        ensureParentDir(full)
        val bytes = text.encodeToByteArray()
        val mode = "wb"
        val file = fopen(full, mode)
        if (file == null) return
        try {
            bytes.usePinned { pinned ->
                var writtenTotal = 0
                val total = bytes.size
                while (writtenTotal < total) {
                    val w = fwrite(pinned.addressOf(writtenTotal), 1.convert(), (total - writtenTotal).convert(), file)
                    if (w <= 0u) break
                    writtenTotal += w.toInt()
                }
            }
            fflush(file)
        } finally {
            fclose(file)
        }
    }

    actual fun readText(path: String): String {
        val full = fullPath(path)
        val file = fopen(full, "rb") ?: return ""
        try {
            if (fseek(file, 0, SEEK_END) != 0) return ""
            val size = ftell(file).toInt()
            rewind(file)
            if (size <= 0) return ""
            val out = ByteArray(size)
            out.usePinned { pinned ->
                var readTotal = 0
                while (readTotal < size) {
                    val r = fread(pinned.addressOf(readTotal), 1.convert(), (size - readTotal).convert(), file)
                    if (r <= 0u) break
                    readTotal += r.toInt()
                }
            }
            return out.decodeToString()
        } finally {
            fclose(file)
        }
    }

    actual fun saveAttachment(id: String, bytes: ByteArray, ext: String): String {
        val filename = "attachments/${id}.${ext}"
        val full = fullPath(filename)
        ensureParentDir(full)
        val mode = "wb"
        val file = fopen(full, mode)
        if (file == null) return filename
        try {
            bytes.usePinned { pinned ->
                var writtenTotal = 0
                val total = bytes.size
                while (writtenTotal < total) {
                    val w = fwrite(pinned.addressOf(writtenTotal), 1.convert(), (total - writtenTotal).convert(), file)
                    if (w <= 0u) break
                    writtenTotal += w.toInt()
                }
            }
            fflush(file)
        } finally {
            fclose(file)
        }
        return filename
    }

    actual fun readAttachment(filename: String): ByteArray? {
        val full = fullPath(filename)
        val file = fopen(full, "rb") ?: return null
        try {
            if (fseek(file, 0, SEEK_END) != 0) return null
            val size = ftell(file).toInt()
            rewind(file)
            if (size <= 0) return ByteArray(0)
            val out = ByteArray(size)
            out.usePinned { pinned ->
                var readTotal = 0
                while (readTotal < size) {
                    val r = fread(pinned.addressOf(readTotal), 1.convert(), (size - readTotal).convert(), file)
                    if (r <= 0u) break
                    readTotal += r.toInt()
                }
            }
            return out
        } finally {
            fclose(file)
        }
    }
}
