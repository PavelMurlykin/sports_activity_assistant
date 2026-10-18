package com.pamurlykin.sportsactivityassistant.data.model

import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.text.AppText
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Times belong to the planned date; an earlier end is on the following day. */
object TrainingTimes {
    private val format = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

    fun parse(text: String): String? = text.trim().takeIf { it.isNotEmpty() }?.let {
        require(Regex("\\d{2}:\\d{2}").matches(it)) { AppText.get(R.string.time_invalid) }
        requireNotNull(runCatching { LocalTime.parse(it, format) }.getOrNull()) { AppText.get(R.string.time_invalid) }
        it
    }

    fun duration(start: String?, end: String?): Int? {
        val from = start?.let { LocalTime.parse(requireNotNull(parse(it)), format) }
        val to = end?.let { LocalTime.parse(requireNotNull(parse(it)), format) }
        if (from == null || to == null) return null
        val minutes = (to.toSecondOfDay() - from.toSecondOfDay()) / 60
        require(minutes != 0) { AppText.get(R.string.time_equal) }
        return if (minutes < 0) minutes + 24 * 60 else minutes
    }

    fun label(start: String?, end: String?): List<String> = when {
        start != null && end != null -> listOf("$start–$end")
        start != null -> listOf(AppText.get(R.string.time_start_value, start))
        end != null -> listOf(AppText.get(R.string.time_end_value, end))
        else -> emptyList()
    }
}
