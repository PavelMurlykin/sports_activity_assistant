package com.pamurlykin.sportsactivityassistant.data

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pamurlykin.sportsactivityassistant.data.model.HistoricalClimbingGrades

internal class ClimbingGradesMigration : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE climbing_routes ADD COLUMN grading_system TEXT NOT NULL DEFAULT 'legacy'")
        db.execSQL("ALTER TABLE climbing_routes ADD COLUMN grade_code TEXT")
        db.execSQL("ALTER TABLE climbing_routes ADD COLUMN speed_course TEXT")
        db.execSQL("ALTER TABLE climbing_routes ADD COLUMN legacy_workout_type TEXT")
        db.query("SELECT id, workout_type, route_difficulty FROM climbing_routes").use { cursor ->
            while (cursor.moveToNext()) {
                val type = cursor.getString(1)
                val grade = HistoricalClimbingGrades.classify(type, cursor.getString(2))
                val unknownType = type.takeUnless { it in setOf("difficulty", "bouldering", "speed") }
                db.execSQL("UPDATE climbing_routes SET grading_system = ?, grade_code = ?, legacy_workout_type = ? WHERE id = ?",
                    arrayOf<Any?>(grade.system, grade.code, unknownType, cursor.getLong(0)))
            }
        }
        // Original difficulty, discipline, result, repeats, UUIDs and relations are untouched.
        db.query("PRAGMA foreign_key_check").use {
            check(!it.moveToFirst()) { AppText.get(R.string.climbing_grades_migration_narusheny_svyazi_istoricheskih_trenirovok_obnovlenie) }
        }
    }
}
