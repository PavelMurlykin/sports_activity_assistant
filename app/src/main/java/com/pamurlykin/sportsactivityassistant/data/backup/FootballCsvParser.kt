package com.pamurlykin.sportsactivityassistant.data.backup

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

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
        val header = text.lineSequence().firstOrNull(String::isNotBlank) ?: error(AppText.get(R.string.football_csv_parser_csv_fayl_pust))
        val delimiter = if (header.count { it == ';' } >= header.count { it == ',' }) ';' else ','
        val records = records(text, delimiter)
        val headers = records.first().cells.map { it.trim() }
        require(headers.distinct().size == headers.size) { AppText.get(R.string.football_csv_parser_csv_povtoryayuschiesya_kolonki_zagolovka) }
        val missing = requiredHeaders - headers.toSet()
        require(missing.isEmpty()) { AppText.get(R.string.football_csv_parser_v_csv_otsutstvuyut_kolonki, missing.sorted().joinToString()) }
        require(records.size > 1) { AppText.get(R.string.football_csv_parser_v_csv_net_trenirovok) }
        return records.drop(1).map { record ->
            require(record.cells.size == headers.size) { AppText.get(R.string.football_csv_parser_stroka_chislo_znacheniy_ne, record.line) }
            val row = headers.zip(record.cells).toMap()
            fun required(name: String) = row.getValue(name).trim().also {
                require(it.isNotEmpty()) { AppText.get(R.string.football_csv_parser_stroka_pole_ne, record.line, name) }
            }
            fun long(name: String) = requireNotNull(required(name).toLongOrNull()) {
                AppText.get(R.string.football_csv_parser_stroka_trebuetsya_tseloe, record.line, name)
            }.also { require(it > 0) { AppText.get(R.string.football_csv_parser_stroka_znachenie_dolzhno, record.line, name) } }
            fun number(name: String) = requireNotNull(required(name).toIntOrNull()) {
                AppText.get(R.string.football_csv_parser_stroka_trebuetsya_tseloe_2, record.line, name)
            }.also { require(it >= 0) { AppText.get(R.string.football_csv_parser_stroka_znachenie_ne, record.line, name) } }
            fun optional(name: String) = row[name]?.trim()?.takeIf(String::isNotEmpty)?.let {
                requireNotNull(it.toIntOrNull()) { AppText.get(R.string.football_csv_parser_stroka_trebuetsya_tseloe, record.line, name) }
            }
            val date = runCatching { LocalDate.parse(required("training_date")) }.getOrElse {
                error(AppText.get(R.string.football_csv_parser_stroka_training_date_ispolzuyte_yyyy_mm_dd, record.line))
            }
            val distance = row["distance_km"]?.trim()?.takeIf(String::isNotEmpty)?.let {
                require(it.length <= 64) { AppText.get(R.string.football_csv_parser_stroka_distance_km_slishkom_dlinnoe, record.line) }
                requireNotNull(it.replace(',', '.').toBigDecimalOrNull()) { AppText.get(R.string.football_csv_parser_stroka_distance_km_nevernoe_chislo, record.line) }
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
                require(result.size <= ImportFiles.MAX_ITEMS) { AppText.get(R.string.football_csv_parser_csv_slishkom_mnogo_zapisey) }
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
                    require(!closed && field.isBlank()) { AppText.get(R.string.football_csv_parser_stroka_kavychka_vnutri_nekavychennogo, line) }
                    field.clear(); quoted = true
                }
                char == delimiter -> endField()
                char == '\n' || char == '\r' -> {
                    if (char == '\r' && text.getOrNull(i + 1) == '\n') i++
                    endRecord(); line++; startLine = line
                }
                closed -> require(char == ' ' || char == '\t') { AppText.get(R.string.football_csv_parser_stroka_simvol_posle_zakryvayuschey, line) }
                else -> field.append(char)
            }
            i++
        }
        require(!quoted) { AppText.get(R.string.football_csv_parser_stroka_nezakrytaya_kavychka, startLine) }
        if (field.isNotEmpty() || cells.isNotEmpty() || closed) endRecord()
        return result
    }
}
