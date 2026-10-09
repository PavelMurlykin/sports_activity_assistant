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
