package com.pamurlykin.sportsactivityassistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingRouteEntity
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.FootballTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingDao {
    @Transaction
    @Query(
        """
        SELECT * FROM trainings
        WHERE user_id = :userId AND training_date BETWEEN :startDate AND :endDate
        ORDER BY training_date, id
        """,
    )
    suspend fun getTrainingBundlesBetween(
        userId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<TrainingBundle>

    @Transaction
    @Query(
        """
        SELECT * FROM trainings
        WHERE sport_id = :sportId
        ORDER BY training_date DESC, id DESC
        """,
    )
    fun observeTrainingBundlesBySport(sportId: Int): Flow<List<TrainingBundle>>

    @Transaction
    @Query("SELECT * FROM trainings ORDER BY training_date DESC, id DESC")
    fun observeAllTrainingBundles(): Flow<List<TrainingBundle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainings(items: List<TrainingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFootballTrainings(items: List<FootballTrainingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClimbingTrainings(items: List<ClimbingTrainingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClimbingRoutes(items: List<ClimbingRouteEntity>)
}
