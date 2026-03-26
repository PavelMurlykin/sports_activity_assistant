package com.pamurlykin.sportsactivityassistant.data

import androidx.room.TypeConverter
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType
import com.pamurlykin.sportsactivityassistant.data.model.PlannedTrainingStatus
import com.pamurlykin.sportsactivityassistant.data.model.RecurrenceFrequency
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    @TypeConverter
    fun fromWorkoutType(value: ClimbingWorkoutType?): String? = value?.storageValue

    @TypeConverter
    fun toWorkoutType(value: String?): ClimbingWorkoutType? = value?.let(ClimbingWorkoutType::fromStorage)

    @TypeConverter
    fun fromRecurrenceFrequency(value: RecurrenceFrequency?): String? = value?.storageValue

    @TypeConverter
    fun toRecurrenceFrequency(value: String?): RecurrenceFrequency? = value?.let(RecurrenceFrequency::fromStorage)

    @TypeConverter
    fun fromPlannedTrainingStatus(value: PlannedTrainingStatus?): String? = value?.storageValue

    @TypeConverter
    fun toPlannedTrainingStatus(value: String?): PlannedTrainingStatus? = value?.let(PlannedTrainingStatus::fromStorage)
}
