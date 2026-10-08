package com.pamurlykin.sportsactivityassistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pamurlykin.sportsactivityassistant.data.dao.PlanningDao
import com.pamurlykin.sportsactivityassistant.data.dao.ReferenceDao
import com.pamurlykin.sportsactivityassistant.data.dao.TrainingDao
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingRouteEntity
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.FootballTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.PlannedTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.RecurrenceRuleEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexSportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.UserEntity
import com.pamurlykin.sportsactivityassistant.data.entity.UserFavoriteComplexEntity
import com.pamurlykin.sportsactivityassistant.data.entity.ImportAliasEntity

@Database(
    entities = [
        UserEntity::class,
        SportEntity::class,
        SportsComplexEntity::class,
        SportsComplexSportEntity::class,
        UserFavoriteComplexEntity::class,
        TrainingEntity::class,
        FootballTrainingEntity::class,
        ClimbingTrainingEntity::class,
        ClimbingRouteEntity::class,
        RecurrenceRuleEntity::class,
        PlannedTrainingEntity::class,
        ImportAliasEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun referenceDao(): ReferenceDao
    abstract fun trainingDao(): TrainingDao
    abstract fun planningDao(): PlanningDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sports_activity_assistant.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }

        val MIGRATION_2_3: Migration = LocalIdentityMigration()
        val MIGRATION_3_4: Migration = ClimbingGradesMigration()
        val MIGRATION_4_5: Migration = ExchangeIdentityMigration()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE football_trainings ADD COLUMN players_per_team INTEGER")
                db.execSQL("ALTER TABLE football_trainings ADD COLUMN duration_minutes INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE climbing_routes_v2 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        climbing_training_id INTEGER NOT NULL,
                        workout_type TEXT NOT NULL,
                        route_difficulty TEXT NOT NULL,
                        is_completed INTEGER NOT NULL,
                        repeat_count INTEGER NOT NULL,
                        FOREIGN KEY(climbing_training_id)
                            REFERENCES climbing_trainings(training_id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO climbing_routes_v2 (
                        id, climbing_training_id, workout_type,
                        route_difficulty, is_completed, repeat_count
                    )
                    SELECT id, climbing_training_id, workout_type,
                           route_difficulty, 1, routes_completed
                    FROM climbing_routes
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE climbing_routes")
                db.execSQL("ALTER TABLE climbing_routes_v2 RENAME TO climbing_routes")
                db.execSQL(
                    "CREATE INDEX index_climbing_routes_climbing_training_id " +
                        "ON climbing_routes(climbing_training_id)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sports_slug ON sports(slug)")
            }
        }
    }
}
