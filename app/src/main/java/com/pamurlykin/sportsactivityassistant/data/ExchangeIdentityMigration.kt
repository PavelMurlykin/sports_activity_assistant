package com.pamurlykin.sportsactivityassistant.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

internal class ExchangeIdentityMigration : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN public_id TEXT NOT NULL DEFAULT ''")
        db.query("SELECT id FROM users").use { cursor ->
            while (cursor.moveToNext()) db.execSQL("UPDATE users SET public_id = ? WHERE id = ?",
                arrayOf<Any>(UUID.randomUUID().toString(), cursor.getLong(0)))
        }
        db.execSQL("CREATE UNIQUE INDEX index_users_public_id ON users(public_id)")
        db.execSQL("ALTER TABLE sports_complexes ADD COLUMN is_initial INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS import_aliases (kind TEXT NOT NULL, source_key TEXT NOT NULL, target_public_id TEXT NOT NULL, PRIMARY KEY(kind, source_key))")
        db.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "Нарушены связи базы; обновление отменено" } }
    }
}
