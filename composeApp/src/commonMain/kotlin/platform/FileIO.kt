package platform

expect object FileIO {
    fun writeText(path: String, text: String)
    fun readText(path: String): String
    fun saveAttachment(id: String, bytes: ByteArray): String // returns filename
    fun readAttachment(filename: String): ByteArray?
}

