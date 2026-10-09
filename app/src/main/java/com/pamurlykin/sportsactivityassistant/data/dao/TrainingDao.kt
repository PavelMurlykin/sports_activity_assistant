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
import com.pamurlykin.sportsactivityassistant.data.model.*
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

    @Query("""
        SELECT sport_id AS sportId, COUNT(*) AS count FROM trainings
        WHERE user_id = :userId AND training_date BETWEEN :start AND :end
          AND (:centerId IS NULL OR sports_complex_id = :centerId)
        GROUP BY sport_id
    """)
    suspend fun sportCounts(userId: Long, start: LocalDate, end: LocalDate, centerId: Long?): List<SportCount>

    @Query("""
        SELECT substr(training_date, 1, 7) AS month, sport_id AS sportId, COUNT(*) AS count FROM trainings
        WHERE user_id = :userId AND training_date BETWEEN :start AND :end
          AND (:centerId IS NULL OR sports_complex_id = :centerId)
        GROUP BY month, sport_id ORDER BY month DESC
    """)
    suspend fun monthCounts(userId: Long, start: LocalDate, end: LocalDate, centerId: Long?): List<MonthSportCount>

    @Query("""
        SELECT COUNT(*) FROM trainings
        WHERE user_id = :userId AND sport_id = :sportId AND training_date BETWEEN :start AND :end
          AND (:centerId IS NULL OR sports_complex_id = :centerId)
    """)
    suspend fun trainingCount(userId: Long, sportId: Int, start: LocalDate, end: LocalDate, centerId: Long?): Int

    @Transaction
    @Query("""
        SELECT * FROM trainings
        WHERE user_id = :userId AND sport_id = :sportId AND training_date BETWEEN :start AND :end
          AND (:centerId IS NULL OR sports_complex_id = :centerId)
        ORDER BY training_date DESC, id DESC LIMIT :limit OFFSET :offset
    """)
    suspend fun trainingPage(userId: Long, sportId: Int, start: LocalDate, end: LocalDate,
        centerId: Long?, limit: Int, offset: Int): List<TrainingBundle>

    @Query("""
        SELECT COUNT(*) AS games,
          COALESCE(SUM(f.team_goals_scored > f.team_goals_conceded), 0) AS wins,
          COALESCE(SUM(f.team_goals_scored = f.team_goals_conceded), 0) AS draws,
          COALESCE(SUM(f.team_goals_scored < f.team_goals_conceded), 0) AS losses,
          COALESCE(SUM(f.team_goals_scored), 0) AS teamScored,
          COALESCE(SUM(f.team_goals_conceded), 0) AS teamConceded,
          COALESCE(SUM(f.user_goals_scored), 0) AS personalGoals,
          COALESCE(SUM(f.user_assists), 0) AS assists,
          COUNT(f.distance_km) AS distanceCount,
          COALESCE(SUM(f.duration_minutes), 0) AS minutes,
          COUNT(f.duration_minutes) AS durationCount,
          COALESCE(SUM(f.players_per_team), 0) AS players,
          COUNT(f.players_per_team) AS playersCount
        FROM trainings t JOIN football_trainings f ON f.training_id = t.id
        WHERE t.user_id = :userId AND t.sport_id = :sportId AND t.training_date BETWEEN :start AND :end
          AND (:centerId IS NULL OR t.sports_complex_id = :centerId)
    """)
    suspend fun footballTotals(userId: Long, sportId: Int, start: LocalDate, end: LocalDate, centerId: Long?): FootballTotals

    // TEXT decimals must not be converted to SQLite REAL: sum exactly in bounded batches.
    @Query("""
        SELECT t.id, f.distance_km AS distanceKm
        FROM trainings t JOIN football_trainings f ON f.training_id = t.id
        WHERE t.user_id = :userId AND t.sport_id = :sportId AND t.training_date BETWEEN :start AND :end
          AND (:centerId IS NULL OR t.sports_complex_id = :centerId)
          AND f.distance_km IS NOT NULL AND t.id > :afterId
        ORDER BY t.id LIMIT 512
    """)
    suspend fun distanceSamples(userId: Long, sportId: Int, start: LocalDate, end: LocalDate,
        centerId: Long?, afterId: Long): List<DistanceSample>

    @Query("""
        SELECT r.workout_type AS workoutType, r.grading_system AS gradingSystem,
          r.grade_code AS gradeCode, r.speed_course AS speedCourse,
          (lower(trim(r.route_difficulty, :whitespace)) = r.grade_code) IS 1 AS gradeMatches,
          COUNT(*) AS rowCount, SUM(r.repeat_count) AS attempts,
          SUM(CASE WHEN r.is_completed THEN r.repeat_count ELSE 0 END) AS completed
        FROM trainings t JOIN climbing_routes r ON r.climbing_training_id = t.id
        WHERE t.user_id = :userId AND t.sport_id = :sportId AND t.training_date BETWEEN :start AND :end
          AND (:centerId IS NULL OR t.sports_complex_id = :centerId)
        GROUP BY r.workout_type, r.grading_system, r.grade_code, r.speed_course, gradeMatches
    """)
    suspend fun climbingBuckets(userId: Long, sportId: Int, start: LocalDate, end: LocalDate, centerId: Long?, whitespace: String = gradeWhitespace): List<ClimbingBucket>

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
