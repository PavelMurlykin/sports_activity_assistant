package com.pamurlykin.sportsactivityassistant.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/** Room executes this migration in a transaction. Never disable foreign keys or drop a parent first. */
internal class LocalIdentityMigration : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        checkForeignKeys(db)
        // Parent-first order; temporary copies have no foreign keys and cannot be cascade-deleted.
        val tables = listOf(
            "users", "trainings", "football_trainings", "climbing_trainings",
            "climbing_routes", "recurrence_rules", "planned_trainings", "user_favorite_complexes",
        )
        // Replay predecessor DDL, not current entities: future changes must not alter this migration.
        // Room validates the resulting schema against the exported v3 schema.
        val definitions = tables.associateWith { table ->
            db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use {
                check(it.moveToFirst()) { "Missing predecessor table: $table" }
                it.getString(0)
            }
        }
        val indexes = buildList {
            tables.filterNot { it == "users" }.forEach { table ->
                db.query("SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL", arrayOf(table)).use {
                    while (it.moveToNext()) add(it.getString(0))
                }
            }
        }
        val sequences = mutableMapOf<String, Long>()
        db.query("SELECT name, seq FROM sqlite_sequence").use {
            while (it.moveToNext()) sequences[it.getString(0)] = it.getLong(1)
        }
        tables.forEach { db.execSQL("CREATE TEMP TABLE _migration3_$it AS SELECT * FROM $it") }
        tables.asReversed().forEach { db.execSQL("DROP TABLE $it") }
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, display_name TEXT, created_at INTEGER NOT NULL)")
        db.execSQL(
            "INSERT INTO users(id, display_name, created_at) " +
                "SELECT id, NULLIF(TRIM(COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')), ''), created_at FROM _migration3_users",
        )
        tables.filterNot { it == "users" }.forEach { table ->
            db.execSQL(requireNotNull(definitions[table]))
            db.execSQL("INSERT INTO $table SELECT * FROM _migration3_$table")
        }
        indexes.forEach(db::execSQL)
        listOf("sports_complexes", "trainings", "climbing_routes", "recurrence_rules", "planned_trainings").forEach { table ->
            db.execSQL("ALTER TABLE $table ADD COLUMN public_id TEXT NOT NULL DEFAULT ''")
            val ids = buildList {
                db.query("SELECT id FROM $table").use { while (it.moveToNext()) add(it.getLong(0)) }
            }
            ids.forEach { id ->
                db.execSQL("UPDATE $table SET public_id = ? WHERE id = ?", arrayOf<Any>(UUID.randomUUID().toString(), id))
            }
            db.execSQL("CREATE UNIQUE INDEX index_${table}_public_id ON $table(public_id)")
        }
        // Preserve even deleted rows' high-water marks: an upgrade must not reassign local keys.
        sequences.filterKeys { it in tables }.forEach { (table, sequence) ->
            db.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf(table))
            db.execSQL("INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)", arrayOf<Any>(table, sequence))
        }
        checkForeignKeys(db)
        tables.forEach { db.execSQL("DROP TABLE _migration3_$it") }
    }

    private fun checkForeignKeys(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "Нарушены связи в локальной базе; обновление отменено" } }
    }
}
