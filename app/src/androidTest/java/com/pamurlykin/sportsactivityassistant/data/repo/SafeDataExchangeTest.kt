package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.backup.*
import com.pamurlykin.sportsactivityassistant.data.entity.*
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SafeDataExchangeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun database() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val date = LocalDate.parse("2020-01-01")
    private val header = "user_id;training_date;sports_complex_id;team_goals_scored;team_goals_conceded;user_goals_scored;user_assists"
    private suspend fun workout(db: AppDatabase, repo: AppRepository, user: Long? = null): Long {
        val localUser = repo.localProfileId()
        val sport = db.referenceDao().getSportBySlug("football")!!
        val center = db.referenceDao().getComplexesForSport(sport.id).first()
        return repo.addCompletedTraining(user ?: localUser, AddCompletedTrainingInput(sport.id, center.id, date, FootballTrainingInput(2, 1, 1, 1, null, null, null)))
    }
    private suspend fun content(repo: AppRepository) = BackupCodec.decode(repo.createBackup()).copy(exportedAt = "")

    @Test fun equivalentTimestampSpellingDoesNotCreateAFalseUuidConflictOnRepeat() = runBlocking<Unit> {
        val db = database()
        try {
            val repo = AppRepository(db)
            val raw = InstrumentationRegistry.getInstrumentation().context.assets.open("exchange-backup-v4.json").bufferedReader().use { it.readText() }
                .replace("00Z", "00.000Z").replace(".123Z", ".123000Z")
            val bytes = raw.toByteArray()
            assertEquals(2, repo.importData(bytes, repo.localProfileId()).importedTrainings)
            assertEquals(2, repo.importData(bytes, repo.localProfileId()).skippedTrainings)
        } finally { db.close() }
    }

    @Test fun previouslyMergedProfilesCannotBeSilentlyClaimedAsSeparate() = runBlocking<Unit> {
        val db = database()
        try {
            val repo = AppRepository(db)
            val bytes = "$header\n10;2020-01-01;999;2;1;1;1\n20;2020-01-01;999;2;1;1;1".toByteArray()
            val parsed = repo.prepareImport(bytes)
            val profiles = parsed.document.profiles
            val first = ImportChoices(selectedProfile = profiles[0].publicId, centerMappings = mapOf(parsed.document.centers.single().publicId!! to null))
            repo.applyImport(parsed, first)
            val second = repo.previewImport(parsed, ImportChoices(selectedProfile = profiles[1].publicId))
            repo.applyImport(parsed, second.choices)
            val before = content(repo)
            val all = repo.previewImport(parsed, ImportChoices(preserveAllProfiles = true))
            assertFalse(all.canApply); assertTrue(all.errors.any { it.contains("нельзя автоматически разделить") })
            assertEquals(before, content(repo))
        } finally { db.close() }
    }

    @Test fun documentedJsonExampleIsValidAndRestoresEveryDeclaredRelationship() = runBlocking<Unit> {
        val db = database()
        try {
            val repo = AppRepository(db)
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("exchange-backup-v4.json").use { it.readBytes() }
            val parsed = repo.prepareImport(bytes)
            val preview = repo.previewImport(parsed)
            assertTrue(preview.errors.toString(), preview.canApply)
            val result = repo.applyImport(parsed, preview.choices)
            assertEquals(2, result.importedTrainings); assertEquals(1, result.importedRules); assertEquals(1, result.importedPlans); assertEquals(1, result.importedFavorites)
            val restored = content(repo)
            assertEquals(parsed.document.trainings.map { it.publicId }.toSet(), restored.trainings.map { it.publicId }.toSet())
            assertEquals(parsed.document.plannedTrainings.single().recurrenceRulePublicId, restored.plannedTrainings.single().recurrenceRulePublicId)
            assertEquals(2, repo.importData(bytes, repo.localProfileId()).skippedTrainings)
        } finally { db.close() }
    }

    @Test fun centerAndProfileUuidConflictsAreVisibleAndOnlyKeepLocalIsPermitted() = runBlocking<Unit> {
        val db = database()
        try {
            val repo = AppRepository(db); workout(db, repo)
            val before = content(repo)
            val center = before.centers.first(); val profile = before.profiles.single()
            val changed = before.copy(exportedAt = Instant.now().toString(), centers = before.centers.map { if (it.publicId == center.publicId) it.copy(name = "Другое имя") else it },
                profiles = listOf(profile.copy(displayName = "Другое имя")))
            val parsed = repo.prepareImport(BackupCodec.encode(changed).toByteArray())
            val preview = repo.previewImport(parsed)
            assertFalse(preview.canApply); assertEquals(2, preview.records.count { it.conflict })
            repo.applyImport(parsed, preview.choices.copy(keepLocalIds = setOf(center.publicId!!, profile.publicId)))
            assertEquals(before, content(repo))
        } finally { db.close() }
    }

    @Test fun borrowedRouteUuidAndInvalidPlanStatusFrequencyOrParentAbortBeforeWriting() = runBlocking<Unit> {
        val db = database()
        try {
            val repo = AppRepository(db)
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("exchange-backup-v4.json").use { it.readBytes() }
            repo.importData(bytes, repo.localProfileId())
            val before = content(repo)
            val climbing = before.trainings.first { it.sportSlug == "climbing" }
            val changed = before.copy(exportedAt = Instant.now().toString(), trainings = listOf(climbing.copy(publicId = java.util.UUID.randomUUID().toString())))
            val parsed = repo.prepareImport(BackupCodec.encode(changed).toByteArray())
            val preview = repo.previewImport(parsed)
            assertFalse(preview.canApply); assertTrue(preview.errors.any { it.contains("другому объекту") })
            listOf(before.copy(plannedTrainings = before.plannedTrainings.map { it.copy(status = "invalid") }),
                before.copy(recurrenceRules = before.recurrenceRules.map { it.copy(frequency = "monthly") }),
                before.copy(plannedTrainings = before.plannedTrainings.map { it.copy(recurrenceRulePublicId = java.util.UUID.randomUUID().toString()) })).forEach { invalid ->
                assertTrue(runCatching { repo.importData(BackupCodec.encode(invalid.copy(exportedAt = Instant.now().toString())).toByteArray(), repo.localProfileId()) }.isFailure)
            }
            assertEquals(before, content(repo))
        } finally { db.close() }
    }

    @Test fun completeRestorePreservesUuidsTimesProfilesRoutesPlansParentFavoritesAndRepeats() = runBlocking<Unit> {
        val source = database(); val target = database()
        try {
            val repo = AppRepository(source); val user = repo.localProfileId()
            workout(source, repo); workout(source, repo)
            val climbing = source.referenceDao().getSportBySlug("climbing")!!
            val center = source.referenceDao().getComplexesForSport(climbing.id).first()
            repo.addCompletedTraining(user, AddCompletedTrainingInput(climbing.id, center.id, date, climbingRoutes = listOf(
                ClimbingRouteInput(ClimbingWorkoutType.BOULDERING, "6A", true, repeatCount = 2),
                ClimbingRouteInput(ClimbingWorkoutType.SPEED, "", false, speedCourse = "other"))))
            val time = Instant.parse("2020-01-01T10:00:00.123Z")
            val rule = source.planningDao().insertRecurrenceRule(RecurrenceRuleEntity(userId = user, sportId = climbing.id,
                sportsComplexId = center.id, startDate = date, endDate = date.plusMonths(2), frequency = RecurrenceFrequency.WEEKLY,
                intervalWeeks = 2, createdAt = time))
            source.planningDao().insertPlannedTraining(PlannedTrainingEntity(userId = user, sportId = climbing.id, sportsComplexId = center.id,
                plannedDate = date.plusWeeks(2), recurrenceRuleId = rule, status = PlannedTrainingStatus.CANCELED, createdAt = time))
            source.referenceDao().insertFavoriteComplexes(listOf(UserFavoriteComplexEntity(userId = user, sportsComplexId = center.id, createdAt = time)))
            val other = source.referenceDao().insertUser(UserEntity(displayName = "Отдельная история", createdAt = time))
            workout(source, repo, other)
            val original = BackupCodec.decode(repo.createBackup())
            val importer = AppRepository(target)
            val parsed = importer.prepareImport(BackupCodec.encode(original).toByteArray())
            assertFalse(importer.previewImport(parsed).canApply)
            val preview = importer.previewImport(parsed, ImportChoices(preserveAllProfiles = true))
            assertTrue(preview.errors.toString(), preview.canApply)
            val result = importer.applyImport(parsed, preview.choices)
            assertEquals(4, result.importedTrainings); assertEquals(1, result.importedPlans); assertEquals(1, result.importedRules); assertEquals(1, result.importedFavorites)
            assertEquals(original.copy(exportedAt = ""), content(importer))
            assertEquals(3, importer.observeStatisticsOverview().first().totalTrainings)
            assertEquals(0, importer.applyImport(parsed, importer.previewImport(parsed, ImportChoices(preserveAllProfiles = true)).choices).importedTrainings)
            assertEquals(4, target.trainingDao().getAllTrainingBundles().size)
        } finally { source.close(); target.close() }
    }

    @Test fun sameUuidWithDifferentPayloadRequiresKeepLocalChoiceAndNeverOverwrites() = runBlocking<Unit> {
        val db = database()
        try {
            val repo = AppRepository(db); workout(db, repo)
            val before = content(repo)
            val row = before.trainings.single()
            val document = before.copy(exportedAt = Instant.now().toString(), trainings = listOf(row.copy(football = row.football!!.copy(teamGoalsScored = 5))))
            val parsed = repo.prepareImport(BackupCodec.encode(document).toByteArray())
            val preview = repo.previewImport(parsed)
            assertFalse(preview.canApply); assertTrue(preview.records.single { it.kind == "training" }.conflict)
            assertTrue(runCatching { repo.applyImport(parsed, preview.choices) }.isFailure)
            assertEquals(before, content(repo))
            assertEquals(1, repo.applyImport(parsed, preview.choices.copy(keepLocalIds = setOf(row.publicId!!))).skippedTrainings)
            assertEquals(before, content(repo))
        } finally { db.close() }
    }

    @Test fun sameContentDifferentUuidsIsOnlyASuggestionAndCanBeSkippedExplicitly() = runBlocking<Unit> {
        val source = database(); val target = database()
        try {
            val repo = AppRepository(source); workout(source, repo); workout(source, repo)
            val importer = AppRepository(target)
            val parsed = importer.prepareImport(repo.createBackup().toByteArray())
            val preview = importer.previewImport(parsed)
            assertEquals(1, preview.records.count { it.possibleMatch })
            assertEquals(2, importer.applyImport(parsed, preview.choices).importedTrainings)
            val alternative = database()
            try {
                val r = AppRepository(alternative); val p = r.previewImport(parsed)
                val skip = p.records.single { it.possibleMatch }.publicId
                assertEquals(1, r.applyImport(parsed, p.choices.copy(skipTrainingIds = setOf(skip))).importedTrainings)
            } finally { alternative.close() }
        } finally { source.close(); target.close() }
    }

    @Test fun csvRequiresProfileAndCenterChoicesAndReimportAliasesSurviveBackupRestore() = runBlocking<Unit> {
        val db = database(); val restored = database()
        try {
            val repo = AppRepository(db)
            val bytes = "$header\n10;2020-01-01;999;2;1;1;1\n20;2020-01-01;999;2;1;1;1".toByteArray()
            val parsed = repo.prepareImport(bytes)
            val preview = repo.previewImport(parsed)
            assertFalse(preview.canApply); assertEquals(2, preview.profiles.size)
            val choices = ImportChoices(preserveAllProfiles = true, centerMappings = mapOf(parsed.document.centers.single().publicId!! to null))
            assertEquals(2, repo.applyImport(parsed, choices).importedTrainings)
            assertEquals(1, repo.observeStatisticsOverview().first().totalTrainings)
            assertEquals(0, repo.applyImport(parsed, repo.previewImport(parsed, ImportChoices(preserveAllProfiles = true)).choices).importedTrainings)
            val importer = AppRepository(restored)
            val copy = importer.prepareImport(repo.createBackup().toByteArray())
            val p = importer.previewImport(copy, ImportChoices(preserveAllProfiles = true))
            assertTrue(p.errors.toString(), p.canApply); importer.applyImport(copy, p.choices)
            val repeated = importer.previewImport(parsed, ImportChoices(preserveAllProfiles = true))
            assertTrue(repeated.errors.toString(), repeated.canApply)
            assertTrue(repeated.centers.single().fixed)
            assertEquals(2, importer.applyImport(parsed, repeated.choices).skippedTrainings)
        } finally { db.close(); restored.close() }
    }

    @Test fun csvSelectedUserDoesNotMixOtherHistoryAndKnownNumberStillRequiresConfirmation() = runBlocking<Unit> {
        val db = database()
        try {
            val repo = AppRepository(db); val user = repo.localProfileId()
            val bytes = "$header\n10;2020-01-01;2;2;1;1;1\n20;2020-01-01;2;9;1;1;1".toByteArray()
            val parsed = repo.prepareImport(bytes)
            val preview = repo.previewImport(parsed)
            assertFalse(preview.centers.single().resolved)
            val choices = ImportChoices(selectedProfile = parsed.document.profiles.first().publicId,
                centerMappings = mapOf(parsed.document.centers.single().publicId!! to db.referenceDao().getComplex(2)!!.publicId))
            assertEquals(1, repo.applyImport(parsed, choices).importedTrainings)
            assertEquals(2, db.trainingDao().getAllTrainingBundles().single().football!!.teamGoalsScored)
            assertEquals(user, db.trainingDao().getAllTrainingBundles().single().training.userId)
        } finally { db.close() }
    }

    @Test fun corruptFileAndSqlFailureRollBackEverythingIncludingNewCentersAndAliases() = runBlocking<Unit> {
        val db = database()
        try {
            val repo = AppRepository(db); val before = content(repo)
            val bytes = "$header\n10;2020-01-01;999;2;1;1;1\n10;2020-01-01;999;2;1;1;1".toByteArray()
            val parsed = repo.prepareImport(bytes)
            val choices = repo.previewImport(parsed).choices.copy(centerMappings = mapOf(parsed.document.centers.single().publicId!! to null))
            val invalid = parsed.copy(document = parsed.document.copy(trainings = parsed.document.trainings.mapIndexed { i, t -> if (i == 1) t.copy(date = "bad") else t }))
            assertTrue(runCatching { repo.applyImport(invalid, choices) }.isFailure); assertEquals(before, content(repo))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_import BEFORE INSERT ON trainings WHEN (SELECT COUNT(*) FROM trainings) >= 1 BEGIN SELECT RAISE(ABORT, 'simulated disk failure'); END")
            assertTrue(runCatching { repo.applyImport(parsed, choices) }.isFailure); assertEquals(before, content(repo))
        } finally { db.close() }
    }

    @Test fun finalApplyRevalidatesAChangedDatabaseAfterPreview() = runBlocking<Unit> {
        val source = database(); val target = database()
        try {
            val repo = AppRepository(source); workout(source, repo)
            val importer = AppRepository(target); val parsed = importer.prepareImport(repo.createBackup().toByteArray())
            val preview = importer.previewImport(parsed)
            importer.applyImport(parsed, preview.choices)
            val t = target.trainingDao().getAllTrainingBundles().single()
            target.trainingDao().insertFootballTraining(t.football!!.copy(teamGoalsScored = 9))
            val before = content(importer)
            assertTrue(runCatching { importer.applyImport(parsed, preview.choices) }.isFailure)
            assertEquals(before, content(importer))
        } finally { source.close(); target.close() }
    }

    @Test fun legacyVersionsAssignStableIdentityAndDoNotCollapseIdenticalRecords() = runBlocking<Unit> {
        (1..3).forEach { version ->
            val db = database()
            try {
                val repo = AppRepository(db); val user = repo.localProfileId()
                val row = TrainingBackup(date = "2020-01-01", sportSlug = "football", centerName = "Старый центр", football = FootballBackup(2, 1, 1, 1))
                val old = BackupDocument(schemaVersion = version, exportedAt = "2026-10-07T00:00:00Z", sports = listOf(SportBackup("football", "Футбол")),
                    centers = listOf(CenterBackup(name = "Старый центр", sportSlugs = listOf("football"))), trainings = listOf(row, row))
                val bytes = BackupCodec.encode(old).toByteArray()
                assertEquals(2, repo.importData(bytes, user).importedTrainings)
                assertEquals(2, repo.importData(bytes, user).skippedTrainings)
            } finally { db.close() }
        }
    }

    @Test fun restoreHistoryDoesNotReenableRemovedCenterSport() = runBlocking<Unit> {
        val source = database(); val target = database()
        try {
            val repo = AppRepository(source); workout(source, repo)
            val original = BackupCodec.decode(repo.createBackup())
            val row = original.trainings.single(); val sourceCenter = original.centers.first { it.publicId == row.centerPublicId }
            val climbing = source.referenceDao().getSportBySlug("climbing")!!
            repo.saveSportsCenter(SaveSportsCenterInput(sourceCenter.legacyId, sourceCenter.name, sourceCenter.city, setOf(climbing.id)))
            val importer = AppRepository(target); val parsed = importer.prepareImport(repo.createBackup().toByteArray())
            val preview = importer.previewImport(parsed)
            assertTrue(preview.errors.toString(), preview.canApply); importer.applyImport(parsed, preview.choices)
            val restoredCenter = target.referenceDao().getAllComplexes().first { it.publicId == sourceCenter.publicId }
            val football = target.referenceDao().getSportBySlug("football")!!
            assertFalse(target.referenceDao().getComplexesForSport(football.id).any { it.id == restoredCenter.id })
            assertEquals(1, importer.observeStatisticsOverview().first().totalTrainings)
            assertTrue(runCatching { importer.addCompletedTraining(importer.localProfileId(), AddCompletedTrainingInput(football.id, restoredCenter.id, date,
                FootballTrainingInput(0, 0, 0, 0, null, null, null))) }.isFailure)
            assertTrue(runCatching { workout(target, importer) }.isSuccess) // Other currently available centers remain usable.
        } finally { source.close(); target.close() }
    }
}
