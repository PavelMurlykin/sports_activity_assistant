package com.pamurlykin.sportsactivityassistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    ],
    version = 1,
    exportSchema = false,
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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
