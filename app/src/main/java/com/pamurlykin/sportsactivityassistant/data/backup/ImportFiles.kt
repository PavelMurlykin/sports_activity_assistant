package com.pamurlykin.sportsactivityassistant.data.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object ImportFiles {
    const val MAX_BYTES = 16 * 1024 * 1024
    const val MAX_ITEMS = 100_000

    fun read(stream: InputStream): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            require(result.size().toLong() + count <= MAX_BYTES) { "Файл превышает лимит 16 МиБ" }
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }

    fun text(bytes: ByteArray): Pair<String, Boolean> {
        require(bytes.size <= MAX_BYTES) { "Файл превышает лимит 16 МиБ" }
        require(bytes.isNotEmpty()) { "Файл пуст" }
        require(!(bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
            (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())))) { "Используйте UTF-8 для JSON и UTF-8/Windows-1251 для CSV" }
        var offset = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        while (offset < bytes.size && when (bytes[offset].toInt()) { 9, 10, 13, 32 -> true; else -> false }) offset++
        val first = bytes.getOrNull(offset)
        val isJson = first == '{'.code.toByte() || first == '['.code.toByte()
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }.getOrElse {
            require(!isJson) { "JSON должен быть корректным UTF-8; повреждённая кодировка" }
            charset("windows-1251").decode(ByteBuffer.wrap(bytes)).toString()
        }.removePrefix("\uFEFF")
        require(text.isNotBlank()) { "Файл пуст" }
        return text to isJson
    }
}
