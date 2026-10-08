package com.pamurlykin.sportsactivityassistant.data.model

import java.time.LocalDate

object TrainingValidation {
    fun date(value: LocalDate) { require(value.year in 1..9999) { "Год должен быть в диапазоне 0001–9999" } }
    fun parseDate(value: String): LocalDate = LocalDate.parse(value).also {
        require(value == it.toString()) { "Используйте дату YYYY-MM-DD" }; date(it)
    }
    /** Imported history may contain future dates; manual entry cannot create a new one. */
    fun completedDate(value: LocalDate, originalDate: LocalDate? = null, today: LocalDate = LocalDate.now()) {
        date(value)
        require(value <= today || value == originalDate) { "Будущую тренировку добавьте в план, а не в результаты" }
    }
    fun recurrence(start: LocalDate, end: LocalDate?, interval: Int) {
        date(start); end?.let(::date)
        require(interval > 0) { "Интервал должен быть положительным" }
        require(end == null || end >= start) { "Дата окончания раньше начала серии" }
    }
}
