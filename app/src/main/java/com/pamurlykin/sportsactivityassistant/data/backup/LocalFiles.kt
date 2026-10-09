package com.pamurlykin.sportsactivityassistant.data.backup

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import android.content.ContentResolver
import android.net.Uri

object LocalFiles {
    fun read(resolver: ContentResolver, uri: Uri): ByteArray =
        requireNotNull(resolver.openInputStream(uri)) { AppText.get(R.string.local_files_ne_udalos_otkryt_fayl_dlya) }.use(ImportFiles::read)

    /** Success means closed output and byte-for-byte readback, not just a write call. */
    fun writeVerified(resolver: ContentResolver, uri: Uri, bytes: ByteArray) {
        try {
            requireNotNull(resolver.openOutputStream(uri, "wt")) { AppText.get(R.string.local_files_ne_udalos_otkryt_fayl_dlya_2) }.use {
                it.write(bytes); it.flush()
            }
            require(read(resolver, uri).contentEquals(bytes)) { AppText.get(R.string.local_files_proverka_zapisannogo_fayla_ne_proydena) }
        } catch (e: Exception) {
            throw IllegalStateException(AppText.get(R.string.local_files_kopiya_ne_podtverzhdena_vybrannyy, e.message), e)
        }
    }
}
