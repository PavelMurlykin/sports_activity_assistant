package com.pamurlykin.sportsactivityassistant.data.model

import java.util.Locale

data class HistoricalGrade(val system: String, val code: String?)

/** Frozen v1-v3 interpretation. Never expand this set when the current catalog changes. */
object HistoricalClimbingGrades {
    private val unambiguousFrench = buildSet {
        for (number in 5..9) for (letter in listOf("a", "b", "c")) {
            add("$number$letter")
            if (number != 9 || letter != "c") add("$number$letter+")
        }
    }

    fun classify(workoutType: String, raw: String): HistoricalGrade {
        val code = raw.trim().lowercase(Locale.ROOT)
        return if (workoutType == "difficulty" && code in unambiguousFrench) {
            HistoricalGrade("french", code)
        } else {
            // The old editor used a common French list for bouldering and speed too.
            // A raw "6A" cannot establish the intended boulder scale or speed course.
            HistoricalGrade("legacy", null)
        }
    }
}
