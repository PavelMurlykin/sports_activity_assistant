package com.pamurlykin.sportsactivityassistant.data.model

import java.util.Locale

data class ClimbingGrade(val code: String, val label: String, val rank: Int)

/** Offline, versioned product catalog; sources and scope are in docs/climbing-grades.md. */
object ClimbingDifficultyCatalog {
    const val VERSION = "2026-10-07"
    const val FRENCH = "french"
    const val FONTAINEBLEAU = "fontainebleau"
    const val NONE = "none"
    const val LEGACY = "legacy"

    // Supported input ranges, not assertions about world records or closed scales.
    private val french = letterGrades(3..9).takeWhile { it != "9c+" }.toGrades(uppercase = false)
    private val fontainebleau = letterGrades(1..9).dropWhile { it != "1b" }
        .takeWhile { it != "9a+" }.toGrades(uppercase = true)
    private val bySystem = mapOf(FRENCH to french, FONTAINEBLEAU to fontainebleau)

    fun grades(system: String): List<ClimbingGrade> = bySystem[system].orEmpty()
    fun find(system: String, value: String): ClimbingGrade? =
        grades(system).firstOrNull { it.code == value.trim().lowercase(Locale.ROOT) }

    /** Comparison is deliberately restricted to one explicitly identified scale. */
    fun hardest(system: String, codes: Iterable<String>): ClimbingGrade? =
        codes.mapNotNull { find(system, it) }.maxByOrNull { it.rank }

    fun systemFor(type: ClimbingWorkoutType): String = when (type) {
        ClimbingWorkoutType.DIFFICULTY -> FRENCH
        ClimbingWorkoutType.BOULDERING -> FONTAINEBLEAU
        ClimbingWorkoutType.SPEED -> NONE
        ClimbingWorkoutType.UNKNOWN -> LEGACY
    }

    fun title(system: String): String = when (system) {
        FRENCH -> "Французская"
        FONTAINEBLEAU -> "Fontainebleau"
        NONE -> "Без категории"
        else -> "Историческая — шкала не подтверждена"
    }

    private fun letterGrades(numbers: IntRange): List<String> = buildList {
        numbers.forEach { number -> listOf("a", "b", "c").forEach { letter ->
            add("$number$letter")
            add("$number$letter+")
        } }
    }

    private fun List<String>.toGrades(uppercase: Boolean): List<ClimbingGrade> = mapIndexed { rank, code ->
        ClimbingGrade(code, if (uppercase) code.uppercase(Locale.ROOT) else code, rank)
    }
}

enum class SpeedCourse(val code: String, val title: String) {
    STANDARD_15M("standard_15m", "Эталонная 15 м"),
    OTHER("other", "Иная трасса");
}
