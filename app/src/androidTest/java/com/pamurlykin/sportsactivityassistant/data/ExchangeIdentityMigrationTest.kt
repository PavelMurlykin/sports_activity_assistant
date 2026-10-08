package com.pamurlykin.sportsactivityassistant.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExchangeIdentityMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun upgradeFromV4AddsStableProfileIdentityWithoutGuessingInitialCenters() = runBlocking<Unit> {
        val name = "exchange-upgrade.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO users VALUES (7, 'Мой профиль', 123), (8, NULL, 124)")
            execSQL("INSERT INTO sports VALUES (1, 'football', 'Футбол')")
            execSQL("INSERT INTO sports_complexes VALUES (42, 'Мой центр', NULL, 125, 'c0bd6ac7-f8be-4ff5-9b40-b063bc81f638')")
            execSQL("INSERT INTO trainings VALUES (10, 7, 1, 42, '2020-01-01', 126, 'e8231d73-c845-4763-bc93-f8dce6114118')")
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, AppDatabase.MIGRATION_4_5).close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_5_6).build()
        val ids: List<String>
        try {
            val users = db.referenceDao().getUsers()
            ids = users.map { it.publicId }; ids.forEach { UUID.fromString(it) }
            assertEquals(2, ids.distinct().size)
            assertEquals(listOf(7L, 8L), users.map { it.id }); assertEquals(123L, users.first().createdAt.toEpochMilli())
            assertFalse(db.referenceDao().getAllComplexes().single().isInitial)
            assertEquals(7L, db.trainingDao().getAllTrainingBundles().single().training.userId)
            assertTrue(db.referenceDao().getImportAliases().isEmpty())
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close() }
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_5_6).build()
        try { assertEquals(ids, reopened.referenceDao().getUsers().map { it.publicId }) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }
}
