package com.pamurlykin.sportsactivityassistant.data.model

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import java.time.LocalDate

object TrainingValidation {
    fun date(value: LocalDate) { require(value.year in 1..9999) { AppText.get(R.string.training_validation_god_dolzhen_byt_v_diapazone) } }
    fun parseDate(value: String): LocalDate = LocalDate.parse(value).also {
        require(value == it.toString()) { AppText.get(R.string.training_validation_ispolzuyte_datu_yyyy_mm_dd) }; date(it)
    }
    /** Imported history may contain future dates; manual entry cannot create a new one. */
    fun completedDate(value: LocalDate, originalDate: LocalDate? = null, today: LocalDate = LocalDate.now()) {
        date(value)
        require(value <= today || value == originalDate) { AppText.get(R.string.training_validation_buduschuyu_trenirovku_dobavte_v_plan) }
    }
    fun recurrence(start: LocalDate, end: LocalDate?, interval: Int) {
        date(start); end?.let(::date)
        require(interval > 0) { AppText.get(R.string.training_validation_interval_dolzhen_byt_polozhitelnym) }
        require(end == null || end >= start) { AppText.get(R.string.training_validation_data_okonchaniya_ranshe_nachala_serii) }
    }
}
