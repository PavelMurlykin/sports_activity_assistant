package com.pamurlykin.sportsactivityassistant.data.backup

import java.math.BigDecimal
import java.time.LocalDate

data class LegacyFootballCsvRow(
    val sourceUserId: Long,
    val trainingDate: LocalDate,
    val sportsComplexId: Long,
    val teamGoalsScored: Int,
    val teamGoalsConceded: Int,
    val userGoalsScored: Int,
    val userAssists: Int,
    val distanceKm: BigDecimal?,
    val playersPerTeam: Int? = null,
    val durationMinutes: Int? = null,
    val sourceLine: Int = 0,
)

object FootballCsvParser {
    private val requiredHeaders = setOf("user_id", "training_date", "sports_complex_id",
        "team_goals_scored", "team_goals_conceded", "user_goals_scored", "user_assists")
    private data class Record(val line: Int, val cells: List<String>)

    fun parse(raw: String): List<LegacyFootballCsvRow> {
        val text = raw.removePrefix("\uFEFF")
        val header = text.lineSequence().firstOrNull(String::isNotBlank) ?: error("CSV-файл пуст")
        val delimiter = if (header.count { it == ';' } >= header.count { it == ',' }) ';' else ','
        val records = records(text, delimiter)
        val headers = records.first().cells.map { it.trim() }
        require(headers.distinct().size == headers.size) { "CSV: повторяющиеся колонки заголовка" }
        val missing = requiredHeaders - headers.toSet()
        require(missing.isEmpty()) { "В CSV отсутствуют колонки: ${missing.sorted().joinToString()}" }
        require(records.size > 1) { "В CSV нет тренировок" }
        return records.drop(1).map { record ->
            require(record.cells.size == headers.size) { "Строка ${record.line}: число значений не совпадает с заголовком" }
            val row = headers.zip(record.cells).toMap()
            fun required(name: String) = row.getValue(name).trim().also {
                require(it.isNotEmpty()) { "Строка ${record.line}, $name: поле не заполнено" }
            }
            fun long(name: String) = requireNotNull(required(name).toLongOrNull()) {
                "Строка ${record.line}, $name: требуется целое число"
            }.also { require(it > 0) { "Строка ${record.line}, $name: значение должно быть положительным" } }
            fun number(name: String) = requireNotNull(required(name).toIntOrNull()) {
                "Строка ${record.line}, $name: требуется целое число в диапазоне Int"
            }.also { require(it >= 0) { "Строка ${record.line}, $name: значение не может быть отрицательным" } }
            fun optional(name: String) = row[name]?.trim()?.takeIf(String::isNotEmpty)?.let {
                requireNotNull(it.toIntOrNull()) { "Строка ${record.line}, $name: требуется целое число" }
            }
            val date = runCatching { LocalDate.parse(required("training_date")) }.getOrElse {
                error("Строка ${record.line}, training_date: используйте YYYY-MM-DD")
            }
            val distance = row["distance_km"]?.trim()?.takeIf(String::isNotEmpty)?.let {
                require(it.length <= 64) { "Строка ${record.line}, distance_km: слишком длинное число" }
                requireNotNull(it.replace(',', '.').toBigDecimalOrNull()) { "Строка ${record.line}, distance_km: неверное число" }
            }
            LegacyFootballCsvRow(long("user_id"), date, long("sports_complex_id"), number("team_goals_scored"),
                number("team_goals_conceded"), number("user_goals_scored"), number("user_assists"), distance,
                optional("players_per_team"), optional("duration_minutes"), record.line)
        }
    }

    /** Quoted separators, doubled quotes and embedded newlines, with physical source line numbers. */
    private fun records(text: String, delimiter: Char): List<Record> {
        val result = mutableListOf<Record>()
        val cells = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        var line = 1
        var startLine = 1
        var i = 0
        fun endField() { cells += field.toString(); field.clear(); closed = false }
        fun endRecord() {
            endField()
            if (cells.any(String::isNotBlank)) {
                require(result.size <= ImportFiles.MAX_ITEMS) { "CSV: слишком много записей" }
                result += Record(startLine, cells.toList())
            }
            cells.clear()
        }
        while (i < text.length) {
            val char = text[i]
            when {
                quoted && char == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                quoted && char == '"' -> { quoted = false; closed = true }
                quoted -> { field.append(char); if (char == '\n') line++ }
                char == '"' -> {
                    require(!closed && field.isBlank()) { "Строка $line: кавычка внутри некавыченного поля" }
                    field.clear(); quoted = true
                }
                char == delimiter -> endField()
                char == '\n' || char == '\r' -> {
                    if (char == '\r' && text.getOrNull(i + 1) == '\n') i++
                    endRecord(); line++; startLine = line
                }
                closed -> require(char == ' ' || char == '\t') { "Строка $line: символ после закрывающей кавычки" }
                else -> field.append(char)
            }
            i++
        }
        require(!quoted) { "Строка $startLine: незакрытая кавычка" }
        if (field.isNotEmpty() || cells.isNotEmpty() || closed) endRecord()
        return result
    }
}
