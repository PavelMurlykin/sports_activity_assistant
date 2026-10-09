package com.pamurlykin.sportsactivityassistant.data.model

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

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
        require(name.isNotBlank()) { AppText.get(R.string.center_names_vvedite_nazvanie_sportivnogo_tsentra) }
        require(name.length <= 200 && city.orEmpty().length <= 200) { AppText.get(R.string.center_names_nazvanie_i_gorod_ne_bolee) }
        require((name + city.orEmpty()).none { it.isISOControl() }) { AppText.get(R.string.center_names_nazvanie_i_gorod_ne_dolzhny) }
    }
}
