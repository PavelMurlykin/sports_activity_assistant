package com.pamurlykin.sportsactivityassistant.data.model

import java.text.Normalizer
import java.util.Locale

/** Shared by manual editing and import matching. Never rewrites migrated history. */
object CenterNames {
    private val whitespace = Regex("[\\s\\p{Z}]+")
    fun clean(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
        .replace(whitespace, " ").trim()
    fun key(name: String, city: String?): Pair<String, String> =
        clean(name).lowercase(Locale.ROOT) to clean(city.orEmpty()).lowercase(Locale.ROOT)
    fun validate(name: String, city: String?) {
        require(name.isNotBlank()) { "Введите название спортивного центра" }
        require(name.length <= 200 && city.orEmpty().length <= 200) { "Название и город: не более 200 символов каждый" }
        require((name + city.orEmpty()).none { it.isISOControl() }) { "Название и город не должны содержать управляющие символы" }
    }
}
