package com.pamurlykin.sportsactivityassistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Transaction
    @Query("SELECT * FROM trainings ORDER BY training_date, id")
    suspend fun getAllTrainingBundles(): List<TrainingBundle>

    @Transaction
    @Query("SELECT * FROM trainings WHERE id = :trainingId LIMIT 1")
    suspend fun getTrainingBundle(trainingId: Long): TrainingBundle?

    @Transaction
    @Query("SELECT * FROM trainings WHERE public_id = :publicId LIMIT 1")
    suspend fun getTrainingByPublicId(publicId: String): TrainingBundle?

    @Insert
    suspend fun insertTraining(item: TrainingEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrainings(items: List<TrainingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFootballTrainings(items: List<FootballTrainingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFootballTraining(item: FootballTrainingEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClimbingTrainings(items: List<ClimbingTrainingEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClimbingTraining(item: ClimbingTrainingEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClimbingRoutes(items: List<ClimbingRouteEntity>)

    @Update
    suspend fun updateTraining(item: TrainingEntity)

    @Update
    suspend fun updateClimbingRoutes(items: List<ClimbingRouteEntity>)

    @Query("DELETE FROM climbing_routes WHERE id IN (:ids)")
    suspend fun deleteClimbingRoutes(ids: List<Long>)

    @Query("DELETE FROM trainings WHERE id = :id")
    suspend fun deleteTraining(id: Long): Int

    @Query("DELETE FROM trainings")
    suspend fun deleteAllTrainings()
}
