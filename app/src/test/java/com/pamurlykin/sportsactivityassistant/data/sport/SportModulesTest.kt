package com.pamurlykin.sportsactivityassistant.data.sport

import com.pamurlykin.sportsactivityassistant.data.backup.ClimbingRouteBackup
import com.pamurlykin.sportsactivityassistant.data.backup.FootballBackup
import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.model.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SportModulesTest : com.pamurlykin.sportsactivityassistant.ResourceTextTest() {
    private val football = AddCompletedTrainingInput(1, 1, LocalDate.parse("2026-10-01"), FootballTrainingInput(0, 0, 0, 0, null, null, null))

    @Test
    fun zeroFootballScoreAndMissingOptionalFieldsAreValid() {
        FootballModule.validate(football)
        assertNull(football.football!!.distanceKm)
    }

    @Test
    fun footballRejectsInvalidTotalsAndOptionalFields() {
        val details = football.football!!
        listOf(details.copy(teamGoalsConceded = -1), details.copy(userGoalsScored = 1),
            details.copy(userAssists = 1), details.copy(distanceKm = BigDecimal("-0.01")),
            details.copy(playersPerTeam = 0), details.copy(durationMinutes = -1)).forEach {
            assertTrue(runCatching { FootballModule.validate(football.copy(football = it)) }.isFailure)
        }
    }

    @Test
    fun mixedSportsPayloadIsRejected() {
        val route = ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY, "6A", true)
        assertTrue(runCatching { FootballModule.validate(football.copy(climbingRoutes = listOf(route))) }.isFailure)
        assertTrue(runCatching { ClimbingModule.validate(football.copy(climbingRoutes = listOf(route))) }.isFailure)
    }

    @Test
    fun backupCodecDoesNotSubstituteUnknownDisciplineOrRepeatCount() {
        val backup = TrainingBackup(date = "2026-10-01", sportSlug = "climbing", centerName = "Центр",
            climbingRoutes = listOf(ClimbingRouteBackup("unknown", "6A", true)))
        assertTrue(runCatching { ClimbingModule.decodeDetails(backup, 2, 1) }.isFailure)
        val zero = backup.copy(climbingRoutes = listOf(ClimbingRouteBackup("difficulty", "6A", true, 0)))
        assertTrue(runCatching { ClimbingModule.validate(ClimbingModule.decodeDetails(zero, 2, 1)) }.isFailure)
    }

    @Test
    fun legacyBackupKeepsAggregatedRepeats() {
        val backup = TrainingBackup(date = "2020-01-01", sportSlug = "climbing", centerName = "Центр",
            climbingRoutes = listOf(ClimbingRouteBackup("difficulty", "6B", true, 4)))
        val input = ClimbingModule.decodeDetails(backup, 2, 1)
        ClimbingModule.validate(input)
        assertEquals(4, input.climbingRoutes.single().repeatCount)
    }

    @Test
    fun footballBackupMustContainExactlyItsOwnDetails() {
        val base = TrainingBackup(date = "2026-10-01", sportSlug = "football", centerName = "Центр")
        assertTrue(runCatching { FootballModule.decodeDetails(base, 1, 1) }.isFailure)
        val mixed = base.copy(football = FootballBackup(0, 0, 0, 0), climbingRoutes = listOf(ClimbingRouteBackup("difficulty", "6A", true)))
        assertTrue(runCatching { FootballModule.decodeDetails(mixed, 1, 1) }.isFailure)
    }

    @Test
    fun registryHasUniqueSupportedSportsAndRejectsUnsupportedModule() {
        assertEquals(setOf("football", "climbing"), SportModules.all.map { it.slug }.toSet())
        assertEquals(SportModules.all.size, SportModules.all.map { it.slug }.distinct().size)
        assertSame(FootballModule, SportModules.require("football"))
        assertNull(SportModules.find("unsupported"))
        assertTrue(runCatching { SportModules.require("unsupported") }.isFailure)
    }
}
