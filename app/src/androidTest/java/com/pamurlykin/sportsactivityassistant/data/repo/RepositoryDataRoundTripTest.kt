package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.FootballTrainingInput
import com.pamurlykin.sportsactivityassistant.data.seed.DemoSeed
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryDataRoundTripTest {
    @Test
    fun jsonBackupImportsNewTrainingAndSkipsDuplicates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            DemoSeed.seed(source)
            DemoSeed.seed(target)
            val sourceRepository = AppRepository(source)
            sourceRepository.addCompletedTraining(
                1,
                AddCompletedTrainingInput(
                    sportId = 1,
                    complexId = 2,
                    date = LocalDate.parse("2026-08-07"),
                    football = FootballTrainingInput(3, 2, 1, 1, BigDecimal("6.5"), 6, 60),
                ),
            )

            val backup = sourceRepository.createBackup()
            val targetRepository = AppRepository(target)
            val first = targetRepository.importData(backup.toByteArray(), 1)
            val second = targetRepository.importData(backup.toByteArray(), 1)

            assertTrue(backup.contains("\"schemaVersion\": 5"))
            assertEquals(4, first.importedTrainings)
            assertEquals(0, first.skippedTrainings)
            assertEquals(0, second.importedTrainings)
            assertEquals(4, second.skippedTrainings)
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun footballCsvImportIsIdempotent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            DemoSeed.seed(database)
            val csv = """
                user_id;training_date;sports_complex_id;team_goals_scored;team_goals_conceded;user_goals_scored;user_assists;distance_km
                1000001;2025-12-14;2;5;3;2;1;7,35
            """.trimIndent().toByteArray()
            val repository = AppRepository(database)

            val parsed = repository.prepareImport(csv)
            val preview = repository.previewImport(parsed)
            assertTrue(!preview.canApply)
            val choices = preview.choices.copy(centerMappings = mapOf(parsed.document.centers.single().publicId!! to database.referenceDao().getComplex(2)!!.publicId))
            assertEquals(1, repository.applyImport(parsed, choices).importedTrainings)
            assertEquals(1, repository.applyImport(parsed, choices).skippedTrainings)
        } finally {
            database.close()
        }
    }
}
