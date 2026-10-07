package com.pamurlykin.sportsactivityassistant.data.backup

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FootballCsvParserTest {
    @Test
    fun parsesFootballHistorySemicolonCsv() {
        val csv = """
            user_id;training_date;sports_complex_id;team_goals_scored;team_goals_conceded;user_goals_scored;user_assists;distance_km
            1000001;2025-12-14;2;5;3;2;1;7,35
            1000001;2025-12-21;2;1;1;0;1;
        """.trimIndent()

        val rows = FootballCsvParser.parse(csv)

        assertEquals(2, rows.size)
        assertEquals(LocalDate.parse("2025-12-14"), rows.first().trainingDate)
        assertEquals(BigDecimal("7.35"), rows.first().distanceKm)
        assertEquals(null, rows.last().distanceKm)
    }

    @Test
    fun supportsQuotedCommaCsv() {
        val csv = "user_id,training_date,sports_complex_id,team_goals_scored,team_goals_conceded,user_goals_scored,user_assists,distance_km\n" +
            "1,2026-01-01,2,3,2,1,0,\"6.50\""
        assertEquals(BigDecimal("6.50"), FootballCsvParser.parse(csv).single().distanceKm)
    }

    @Test
    fun rejectsMissingColumns() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            FootballCsvParser.parse("user_id;training_date\n1;2026-01-01")
        }
        assert(error.message!!.contains("отсутствуют колонки"))
    }
}
