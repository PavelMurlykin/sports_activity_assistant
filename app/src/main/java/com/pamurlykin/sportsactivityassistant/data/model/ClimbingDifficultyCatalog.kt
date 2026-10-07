package com.pamurlykin.sportsactivityassistant.data.model

/**
 * Provisional French grading scale for locally recorded sport climbing routes.
 * Values are ordered so aggregate statistics can determine
 * the hardest completed route without parsing display strings.
 */
object ClimbingDifficultyCatalog {
    val values: List<String> = buildList {
        addAll(listOf("3", "4", "4+", "5", "5A", "5A+", "5B", "5B+", "5C", "5C+"))
        for (grade in 6..9) {
            for (letter in listOf("A", "B", "C")) {
                add("$grade$letter")
                if (grade != 9 || letter != "C") add("$grade$letter+")
            }
        }
    }

    private val ranks = values.withIndex().associate { (index, value) -> value to index }

    fun normalize(value: String): String = value.trim().uppercase()

    fun isValid(value: String): Boolean = normalize(value) in ranks

    fun hardest(values: Iterable<String>): String? {
        return values
            .map(::normalize)
            .filter(ranks::containsKey)
            .maxByOrNull { ranks.getValue(it) }
    }
}
