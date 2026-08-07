package com.pamurlykin.sportsactivityassistant.data.backup

import java.math.BigDecimal
import java.time.LocalDate

data class LegacyFootballCsvRow(
    val telegramUserId: Long,
    val trainingDate: LocalDate,
    val sportsComplexId: Long,
    val teamGoalsScored: Int,
    val teamGoalsConceded: Int,
    val userGoalsScored: Int,
    val userAssists: Int,
    val distanceKm: BigDecimal?,
)

object FootballCsvParser {
    private val requiredHeaders = setOf(
        "user_id",
        "training_date",
        "sports_complex_id",
        "team_goals_scored",
        "team_goals_conceded",
        "user_goals_scored",
        "user_assists",
        "distance_km",
    )

    fun parse(raw: String): List<LegacyFootballCsvRow> {
        val records = raw.lineSequence().filter(String::isNotBlank).toList()
        require(records.isNotEmpty()) { "CSV-файл пуст" }
        val delimiter = if (countUnquoted(records.first(), ';') >= countUnquoted(records.first(), ',')) ';' else ','
        val headers = splitRecord(records.first(), delimiter).map { it.trim() }
        val missing = requiredHeaders - headers.toSet()
        require(missing.isEmpty()) { "В CSV отсутствуют колонки: ${missing.sorted().joinToString()}" }

        return records.drop(1).mapIndexed { index, record ->
            val values = splitRecord(record, delimiter)
            require(values.size == headers.size) { "Строка ${index + 2}: число значений не совпадает с заголовком" }
            val row = headers.zip(values).toMap()
            fun required(name: String): String = row.getValue(name).trim().also {
                require(it.isNotEmpty()) { "Строка ${index + 2}: поле $name не заполнено" }
            }
            fun nonNegative(name: String): Int = required(name).toInt().also {
                require(it >= 0) { "Строка ${index + 2}: поле $name не может быть отрицательным" }
            }

            LegacyFootballCsvRow(
                telegramUserId = required("user_id").toLong().also { require(it > 0) },
                trainingDate = LocalDate.parse(required("training_date")),
                sportsComplexId = required("sports_complex_id").toLong().also { require(it > 0) },
                teamGoalsScored = nonNegative("team_goals_scored"),
                teamGoalsConceded = nonNegative("team_goals_conceded"),
                userGoalsScored = nonNegative("user_goals_scored"),
                userAssists = nonNegative("user_assists"),
                distanceKm = row.getValue("distance_km").trim().takeIf(String::isNotEmpty)
                    ?.replace(',', '.')
                    ?.toBigDecimal()
                    ?.also { require(it >= BigDecimal.ZERO) },
            )
        }
    }

    private fun countUnquoted(value: String, delimiter: Char): Int = splitRecord(value, delimiter).size

    private fun splitRecord(record: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < record.length) {
            val char = record[index]
            when {
                char == '"' && quoted && record.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        require(!quoted) { "В CSV обнаружена незакрытая кавычка" }
        result += current.toString()
        return result
    }
}
