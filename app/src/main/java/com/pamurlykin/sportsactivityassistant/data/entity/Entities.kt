package com.pamurlykin.sportsactivityassistant.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType
import com.pamurlykin.sportsactivityassistant.data.model.PlannedTrainingStatus
import com.pamurlykin.sportsactivityassistant.data.model.RecurrenceFrequency
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "users",
    indices = [Index(value = ["telegram_user_id"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "telegram_user_id") val telegramUserId: Long,
    val username: String?,
    @ColumnInfo(name = "first_name") val firstName: String?,
    @ColumnInfo(name = "last_name") val lastName: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)

@Entity(tableName = "sports")
data class SportEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val slug: String,
    val title: String,
)

@Entity(
    tableName = "sports_complexes",
    indices = [Index(value = ["name", "city"], unique = true)],
)
data class SportsComplexEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val city: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)

@Entity(
    tableName = "sports_complex_sports",
    foreignKeys = [
        ForeignKey(
            entity = SportsComplexEntity::class,
            parentColumns = ["id"],
            childColumns = ["sports_complex_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SportEntity::class,
            parentColumns = ["id"],
            childColumns = ["sport_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sports_complex_id"]),
        Index(value = ["sport_id"]),
        Index(value = ["sports_complex_id", "sport_id"], unique = true),
    ],
)
data class SportsComplexSportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sports_complex_id") val sportsComplexId: Long,
    @ColumnInfo(name = "sport_id") val sportId: Int,
)

@Entity(
    tableName = "user_favorite_complexes",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SportsComplexEntity::class,
            parentColumns = ["id"],
            childColumns = ["sports_complex_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["sports_complex_id"]),
        Index(value = ["user_id", "sports_complex_id"], unique = true),
    ],
)
data class UserFavoriteComplexEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "sports_complex_id") val sportsComplexId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)

@Entity(
    tableName = "trainings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SportEntity::class,
            parentColumns = ["id"],
            childColumns = ["sport_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SportsComplexEntity::class,
            parentColumns = ["id"],
            childColumns = ["sports_complex_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["sport_id"]),
        Index(value = ["sports_complex_id"]),
        Index(value = ["user_id", "training_date"]),
    ],
)
data class TrainingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "sport_id") val sportId: Int,
    @ColumnInfo(name = "sports_complex_id") val sportsComplexId: Long,
    @ColumnInfo(name = "training_date") val trainingDate: LocalDate,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)

@Entity(
    tableName = "football_trainings",
    foreignKeys = [
        ForeignKey(
            entity = TrainingEntity::class,
            parentColumns = ["id"],
            childColumns = ["training_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["training_id"], unique = true)],
)
data class FootballTrainingEntity(
    @PrimaryKey
    @ColumnInfo(name = "training_id")
    val trainingId: Long,
    @ColumnInfo(name = "team_goals_scored") val teamGoalsScored: Int,
    @ColumnInfo(name = "team_goals_conceded") val teamGoalsConceded: Int,
    @ColumnInfo(name = "user_goals_scored") val userGoalsScored: Int,
    @ColumnInfo(name = "user_assists") val userAssists: Int,
    @ColumnInfo(name = "distance_km") val distanceKm: BigDecimal?,
)

@Entity(
    tableName = "climbing_trainings",
    foreignKeys = [
        ForeignKey(
            entity = TrainingEntity::class,
            parentColumns = ["id"],
            childColumns = ["training_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["training_id"], unique = true)],
)
data class ClimbingTrainingEntity(
    @PrimaryKey
    @ColumnInfo(name = "training_id")
    val trainingId: Long,
)

@Entity(
    tableName = "climbing_routes",
    foreignKeys = [
        ForeignKey(
            entity = ClimbingTrainingEntity::class,
            parentColumns = ["training_id"],
            childColumns = ["climbing_training_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["climbing_training_id"])],
)
data class ClimbingRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "climbing_training_id") val climbingTrainingId: Long,
    @ColumnInfo(name = "workout_type") val workoutType: ClimbingWorkoutType,
    @ColumnInfo(name = "route_difficulty") val routeDifficulty: String,
    @ColumnInfo(name = "routes_completed") val routesCompleted: Int,
)

@Entity(
    tableName = "recurrence_rules",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SportEntity::class,
            parentColumns = ["id"],
            childColumns = ["sport_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SportsComplexEntity::class,
            parentColumns = ["id"],
            childColumns = ["sports_complex_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["sport_id"]),
        Index(value = ["sports_complex_id"]),
    ],
)
data class RecurrenceRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "sport_id") val sportId: Int,
    @ColumnInfo(name = "sports_complex_id") val sportsComplexId: Long,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate?,
    val frequency: RecurrenceFrequency,
    @ColumnInfo(name = "interval_weeks") val intervalWeeks: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)

@Entity(
    tableName = "planned_trainings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SportEntity::class,
            parentColumns = ["id"],
            childColumns = ["sport_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SportsComplexEntity::class,
            parentColumns = ["id"],
            childColumns = ["sports_complex_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RecurrenceRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurrence_rule_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["sport_id"]),
        Index(value = ["sports_complex_id"]),
        Index(value = ["planned_date"]),
        Index(value = ["recurrence_rule_id"]),
    ],
)
data class PlannedTrainingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "sport_id") val sportId: Int,
    @ColumnInfo(name = "sports_complex_id") val sportsComplexId: Long,
    @ColumnInfo(name = "planned_date") val plannedDate: LocalDate,
    @ColumnInfo(name = "recurrence_rule_id") val recurrenceRuleId: Long? = null,
    val status: PlannedTrainingStatus = PlannedTrainingStatus.PLANNED,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)

data class ClimbingTrainingWithRoutes(
    @Embedded val climbingTraining: ClimbingTrainingEntity,
    @Relation(
        parentColumn = "training_id",
        entityColumn = "climbing_training_id",
    )
    val routes: List<ClimbingRouteEntity>,
)

data class TrainingBundle(
    @Embedded val training: TrainingEntity,
    @Relation(
        parentColumn = "sport_id",
        entityColumn = "id",
    )
    val sport: SportEntity,
    @Relation(
        parentColumn = "sports_complex_id",
        entityColumn = "id",
    )
    val complex: SportsComplexEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "training_id",
    )
    val football: FootballTrainingEntity?,
    @Relation(
        entity = ClimbingTrainingEntity::class,
        parentColumn = "id",
        entityColumn = "training_id",
    )
    val climbing: ClimbingTrainingWithRoutes?,
)
