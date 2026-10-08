package com.pamurlykin.sportsactivityassistant.data.backup

import android.content.ContentResolver
import android.net.Uri

object LocalFiles {
    fun read(resolver: ContentResolver, uri: Uri): ByteArray =
        requireNotNull(resolver.openInputStream(uri)) { "Не удалось открыть файл для чтения" }.use(ImportFiles::read)

    /** Success means closed output and byte-for-byte readback, not just a write call. */
    fun writeVerified(resolver: ContentResolver, uri: Uri, bytes: ByteArray) {
        try {
            requireNotNull(resolver.openOutputStream(uri, "wt")) { "Не удалось открыть файл для записи" }.use {
                it.write(bytes); it.flush()
            }
            require(read(resolver, uri).contentEquals(bytes)) { "Проверка записанного файла не пройдена" }
        } catch (e: Exception) {
            throw IllegalStateException("Копия не подтверждена: ${e.message}. Выбранный файл может быть неполным; не используйте его для восстановления.", e)
        }
    }
}
