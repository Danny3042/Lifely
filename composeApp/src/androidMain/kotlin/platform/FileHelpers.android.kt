package platform

actual fun writeTextFile(path: String, text: String) { FileIO.writeText(path, text) }
actual fun readTextFile(path: String): String = FileIO.readText(path)
actual fun saveAttachmentFile(id: String, bytes: ByteArray): String = FileIO.saveAttachment(id, bytes, "jpg")
actual fun readAttachmentFile(filename: String): ByteArray? = FileIO.readAttachment(filename)
