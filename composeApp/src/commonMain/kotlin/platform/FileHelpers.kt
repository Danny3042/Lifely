package platform

expect fun writeTextFile(path: String, text: String)
expect fun readTextFile(path: String): String
expect fun saveAttachmentFile(id: String, bytes: ByteArray): String
expect fun readAttachmentFile(filename: String): ByteArray?

