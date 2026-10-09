package com.pamurlykin.sportsactivityassistant.data.backup

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

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
            require(result.size().toLong() + count <= MAX_BYTES) { AppText.get(R.string.import_files_fayl_prevyshaet_limit_16_mib) }
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }

    fun text(bytes: ByteArray): Pair<String, Boolean> {
        require(bytes.size <= MAX_BYTES) { AppText.get(R.string.import_files_fayl_prevyshaet_limit_16_mib) }
        require(bytes.isNotEmpty()) { AppText.get(R.string.import_files_fayl_pust) }
        require(!(bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
            (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())))) { AppText.get(R.string.import_files_ispolzuyte_utf_8_dlya_json_i) }
        var offset = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        while (offset < bytes.size && when (bytes[offset].toInt()) { 9, 10, 13, 32 -> true; else -> false }) offset++
        val first = bytes.getOrNull(offset)
        val isJson = first == '{'.code.toByte() || first == '['.code.toByte()
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }.getOrElse {
            require(!isJson) { AppText.get(R.string.import_files_json_dolzhen_byt_korrektnym_utf_8) }
            charset("windows-1251").decode(ByteBuffer.wrap(bytes)).toString()
        }.removePrefix("\uFEFF")
        require(text.isNotBlank()) { AppText.get(R.string.import_files_fayl_pust) }
        return text to isJson
    }
}
