package com.pamurlykin.sportsactivityassistant.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest : com.pamurlykin.sportsactivityassistant.ResourceTextTest() {
    @Test fun speedTimesRoundTripOnlyInVersion9AndOlderFilesHaveNoInventedTime() {
        val route = ClimbingRouteBackup("speed", "", true, gradingSystem = "none", speedCourse = "standard_15m", speedTimeMillis = 8530)
        val document = climbingDocument(9, listOf(route))
        val raw = BackupCodec.encode(document)
        assertEquals(document, BackupCodec.decode(raw))
        assertTrue(runCatching { BackupCodec.encode(document.copy(schemaVersion = 8)) }.isFailure)
        assertTrue(runCatching { BackupCodec.decode(raw.replace("\"schemaVersion\": 9", "\"schemaVersion\": 8")) }.isFailure)
        val old = climbingDocument(8, listOf(route.copy(speedTimeMillis = null)))
        val oldRaw = BackupCodec.encode(old)
        assertFalse(oldRaw.contains("speedTimeMillis"))
        assertNull(BackupCodec.decode(oldRaw).trainings.single().climbingRoutes.single().speedTimeMillis)
    }
    private fun climbingDocument(version: Int, routes: List<ClimbingRouteBackup>) = BackupDocument(
        schemaVersion = version, exportedAt = "2026-10-07T00:00:00Z", sports = emptyList(), centers = emptyList(),
        trainings = listOf(TrainingBackup(date = "2026-10-07", sportSlug = "climbing", centerName = "Центр", climbingRoutes = routes)),
    )

    @Test fun version3KeepsScaleCourseAndRawHistoricalLabels() {
        val source = climbingDocument(3, listOf(
            ClimbingRouteBackup("difficulty", "6a+", true, gradingSystem = "french", gradeCode = "6a+"),
            ClimbingRouteBackup("bouldering", "6A+", false, gradingSystem = "fontainebleau", gradeCode = "6a+"),
            ClimbingRouteBackup("speed", "", true, gradingSystem = "none", speedCourse = "other"),
            ClimbingRouteBackup("old type", "  raw label  ", false, 3, "legacy", legacyWorkoutType = "old type"),
        ))
        assertEquals(source, BackupCodec.decode(BackupCodec.encode(source)))
    }

    @Test fun oldVersionsAreExplicitlyAdaptedWithoutRewritingOriginalGrade() {
        (1..2).forEach { version ->
            val source = climbingDocument(version, listOf(
                ClimbingRouteBackup("difficulty", " 6A+ ", true, 4),
                ClimbingRouteBackup("bouldering", "6A", false, 2),
                ClimbingRouteBackup("speed", "6A", true),
                ClimbingRouteBackup("difficulty", "not known", false),
            ))
            val restored = BackupCodec.decode(BackupCodec.encode(source))
            assertEquals(3, restored.schemaVersion)
            val routes = restored.trainings.single().climbingRoutes
            assertEquals(listOf("french", "legacy", "legacy", "legacy"), routes.map { it.gradingSystem })
            assertEquals("6a+", routes.first().gradeCode)
            assertEquals(" 6A+ ", routes.first().routeDifficulty)
            assertEquals(4, routes.first().repeatCount)
            assertEquals(restored, BackupCodec.decode(BackupCodec.encode(restored)))
        }
    }

    @Test fun refusesMissingMetadataAndSpoofedVersions() {
        val old = ClimbingRouteBackup("difficulty", "6A", true)
        assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(climbingDocument(3, listOf(old)))) }.isFailure)
        assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(climbingDocument(2, listOf(old.copy(gradingSystem = "french", gradeCode = "6a"))))) }.isFailure)
        assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(climbingDocument(999, emptyList()))) }.isFailure)
    }

    @Test
    fun backupRoundTripKeepsSportSpecificData() {
        val source = BackupDocument(
            exportedAt = "2026-08-07T10:00:00Z",
            sports = listOf(SportBackup("football", "Футбол")),
            centers = listOf(CenterBackup(2, "Арена", "Москва", listOf("football"))),
            trainings = listOf(
                TrainingBackup(
                    date = "2026-08-01",
                    sportSlug = "football",
                    centerName = "Арена",
                    centerCity = "Москва",
                    football = FootballBackup(4, 2, 2, 1, "7.2", 6, 60),
                ),
            ),
        )

        val json = BackupCodec.encode(source)
        val restored = BackupCodec.decode(json)

        assertTrue(json.contains("schemaVersion"))
        assertEquals(source, restored)
    }

    @Test fun archiveMetadataRequiresVersion5AndOldCopiesDefaultToActive() {
        val center = CenterBackup(name = "Архив", sportSlugs = emptyList(), isArchived = true)
        val current = climbingDocument(5, emptyList()).copy(centers = listOf(center))
        assertEquals(current, BackupCodec.decode(BackupCodec.encode(current)))
        assertTrue(runCatching { BackupCodec.encode(current.copy(schemaVersion = 4)) }.isFailure)
        val old = current.copy(schemaVersion = 4, centers = listOf(center.copy(isArchived = false)))
        assertFalse(BackupCodec.decode(BackupCodec.encode(old)).centers.single().isArchived)
        assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(current).replace("\"schemaVersion\": 5", "\"schemaVersion\": 4")) }.isFailure)
    }

    @Test fun schedulePreferencesRequireVersion7AndOldFilesKeepOptionalValuesEmpty() {
        val plan = PlannedTrainingBackup("2020-02-29", "football", "Арена", startTime = "23:30", endTime = "01:00")
        val current = climbingDocument(7, emptyList()).copy(plannedTrainings = listOf(plan),
            primaryCenters = listOf(PrimaryCenterBackup("profile", "football", "center")))
        val raw = BackupCodec.encode(current)
        assertEquals(current, BackupCodec.decode(raw))
        assertTrue(runCatching { BackupCodec.encode(current.copy(schemaVersion = 6)) }.isFailure)
        assertTrue(runCatching { BackupCodec.decode(raw.replace("\"schemaVersion\": 7", "\"schemaVersion\": 6")) }.isFailure)
        val old = BackupCodec.decode(BackupCodec.encode(climbingDocument(6, emptyList()).copy(plannedTrainings = listOf(plan.copy(startTime = null, endTime = null)))))
        assertNull(old.plannedTrainings.single().startTime)
        assertTrue(old.primaryCenters.isEmpty())
    }

    @Test fun calendarMetadataRequiresVersion6AndCannotBeDowngradedSilently() {
        val plan = PlannedTrainingBackup("2020-02-29","football","Арена", status = "completed",
            recurrenceRulePublicId = "rule", occurrenceDate = "2020-02-29", completedTrainingPublicId = "result")
        val rule = RecurrenceRuleBackup("2020-02-29", sportSlug = "football", centerName = "Арена", isCanceled = true)
        val current = climbingDocument(6,emptyList()).copy(plannedTrainings = listOf(plan), recurrenceRules = listOf(rule))
        assertEquals(current,BackupCodec.decode(BackupCodec.encode(current)))
        assertTrue(runCatching { BackupCodec.encode(current.copy(schemaVersion = 5)) }.isFailure)
        assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(current).replace("\"schemaVersion\": 6","\"schemaVersion\": 5")) }.isFailure)
    }

    @Test fun version5PlansDefaultToUnlinkedAndSeriesToActive() {
        val old = climbingDocument(5,emptyList()).copy(plannedTrainings = listOf(PlannedTrainingBackup("2020-02-29","football","Арена")),
            recurrenceRules = listOf(RecurrenceRuleBackup("2020-02-29",sportSlug = "football",centerName = "Арена")))
        val encoded = BackupCodec.encode(old)
        assertFalse(encoded.contains("occurrenceDate")); assertFalse(encoded.contains("completedTrainingPublicId")); assertFalse(encoded.contains("isCanceled"))
        assertEquals(old,BackupCodec.decode(encoded))
    }

    @Test
    fun decodesWindows1251CsvText() {
        val text = "Футбол;Москва"
        assertEquals(text, BackupCodec.decodeText(text.toByteArray(charset("windows-1251"))))
    }
    @Test fun sportSelectionsRequireVersion8IncludingExplicitEmptySelections() {
        for (slugs in listOf(listOf("football"), emptyList())) {
            val document = climbingDocument(8, emptyList()).copy(sportSelections = listOf(SportSelectionBackup("profile", slugs)))
            val raw = BackupCodec.encode(document)
            assertEquals(document, BackupCodec.decode(raw))
            assertTrue(runCatching { BackupCodec.encode(document.copy(schemaVersion = 7)) }.isFailure)
            assertTrue(runCatching { BackupCodec.decode(raw.replace("\"schemaVersion\": 8", "\"schemaVersion\": 7")) }.isFailure)
        }
        for (version in 1..7) {
            val raw = BackupCodec.encode(climbingDocument(version, emptyList()))
            assertFalse(raw.contains("sportSelections"))
            assertTrue(BackupCodec.decode(raw).sportSelections.isEmpty())
        }
    }

}
