package com.pamurlykin.sportsactivityassistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.pamurlykin.sportsactivityassistant.data.entity.PlannedTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.RecurrenceRuleEntity
import java.time.LocalDate

@Dao
interface PlanningDao {
    @Query("""
        SELECT p.sport_id AS sportId, MIN(p.planned_date) AS date FROM planned_trainings p
        LEFT JOIN recurrence_rules r ON r.id = p.recurrence_rule_id
        WHERE p.user_id = :userId AND p.planned_date BETWEEN :start AND :end
          AND p.status = 'planned' AND p.completed_training_id IS NULL
          AND (r.id IS NULL OR NOT r.is_canceled)
          AND (:allCenters OR p.sports_complex_id IN (:centerIds))
        GROUP BY p.sport_id
    """)
    suspend fun nextPlannedDates(userId: Long, start: LocalDate, end: LocalDate, centerIds: List<Long>,
        allCenters: Boolean = centerIds.isEmpty()): List<com.pamurlykin.sportsactivityassistant.data.model.SportDate>

    @Query("""
        SELECT * FROM recurrence_rules WHERE user_id = :userId AND NOT is_canceled
          AND frequency = 'weekly' AND start_date <= :end AND (end_date IS NULL OR end_date >= :start)
          AND (:allCenters OR sports_complex_id IN (:centerIds))
    """)
    suspend fun activeRulesForStatistics(userId: Long, start: LocalDate, end: LocalDate, centerIds: List<Long>,
        allCenters: Boolean = centerIds.isEmpty()): List<RecurrenceRuleEntity>

    @Query("""
        SELECT recurrence_rule_id AS ruleId, COALESCE(occurrence_date, planned_date) AS date
        FROM planned_trainings WHERE user_id = :userId AND recurrence_rule_id IS NOT NULL
          AND COALESCE(occurrence_date, planned_date) BETWEEN :start AND :end
    """)
    suspend fun overriddenOccurrences(userId: Long, start: LocalDate, end: LocalDate): List<com.pamurlykin.sportsactivityassistant.data.model.OverriddenOccurrence>

    @Query("SELECT * FROM planned_trainings WHERE id = :id")
    suspend fun getPlan(id: Long): PlannedTrainingEntity?
    @Query("SELECT * FROM recurrence_rules WHERE id = :id")
    suspend fun getRule(id: Long): RecurrenceRuleEntity?
    @Query("SELECT * FROM planned_trainings WHERE recurrence_rule_id = :ruleId AND COALESCE(occurrence_date, planned_date) = :date ORDER BY id LIMIT 1")
    suspend fun getOccurrence(ruleId: Long, date: LocalDate): PlannedTrainingEntity?
    @Query("SELECT * FROM planned_trainings WHERE completed_training_id = :id")
    suspend fun getPlanForResult(id: Long): PlannedTrainingEntity?
    @Update
    suspend fun updatePlan(item: PlannedTrainingEntity)
    @Update
    suspend fun updateRule(item: RecurrenceRuleEntity)
    @Query("SELECT id FROM planned_trainings UNION ALL SELECT id FROM recurrence_rules")
    fun observeChanges(): Flow<List<Long>>

    @Query(
        """
        SELECT * FROM planned_trainings
        WHERE user_id = :userId
            AND planned_date BETWEEN :startDate AND :endDate
        ORDER BY planned_date, id
        """,
    )
    suspend fun getPlannedTrainingsBetween(
        userId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<PlannedTrainingEntity>

    @Query(
        """
        SELECT * FROM recurrence_rules
        WHERE user_id = :userId
            AND start_date <= :endDate
            AND (end_date IS NULL OR end_date >= :startDate)
        ORDER BY start_date
        """,
    )
    suspend fun getRecurrenceRulesOverlapping(
        userId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<RecurrenceRuleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlannedTraining(item: PlannedTrainingEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecurrenceRule(item: RecurrenceRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlannedTrainings(items: List<PlannedTrainingEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecurrenceRules(items: List<RecurrenceRuleEntity>)

    @Query("SELECT * FROM planned_trainings ORDER BY planned_date, id")
    suspend fun getAllPlannedTrainings(): List<PlannedTrainingEntity>

    @Query("SELECT * FROM recurrence_rules ORDER BY start_date, id")
    suspend fun getAllRecurrenceRules(): List<RecurrenceRuleEntity>
}
