package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.withTransaction
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.backup.BackupCodec
import com.pamurlykin.sportsactivityassistant.data.backup.BackupDocument
import com.pamurlykin.sportsactivityassistant.data.backup.CenterBackup
import com.pamurlykin.sportsactivityassistant.data.backup.ClimbingRouteBackup
import com.pamurlykin.sportsactivityassistant.data.backup.FootballBackup
import com.pamurlykin.sportsactivityassistant.data.backup.FootballCsvParser
import com.pamurlykin.sportsactivityassistant.data.backup.ImportResult
import com.pamurlykin.sportsactivityassistant.data.backup.PlannedTrainingBackup
import com.pamurlykin.sportsactivityassistant.data.backup.RecurrenceRuleBackup
import com.pamurlykin.sportsactivityassistant.data.backup.SportBackup
import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingRouteEntity
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.FootballTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.PlannedTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.RecurrenceRuleEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexSportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingEntity
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.AddPlannedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingDifficultyCatalog
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType
import com.pamurlykin.sportsactivityassistant.data.model.ComplexOptionUiModel
import com.pamurlykin.sportsactivityassistant.data.model.PlannedTrainingStatus
import com.pamurlykin.sportsactivityassistant.data.model.RecurrenceFrequency
import com.pamurlykin.sportsactivityassistant.data.model.SaveSportsCenterInput
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleDayUiModel
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleEventState
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleEventUiModel
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleMonthUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportStatisticsUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportsCenterUiModel
import com.pamurlykin.sportsactivityassistant.data.model.StatisticsOverviewUiModel
import com.pamurlykin.sportsactivityassistant.data.model.TrainingSessionUiModel
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class AppRepository(private val database: AppDatabase) {
    fun observeStatisticsOverview(): Flow<StatisticsOverviewUiModel> = combine(
        database.referenceDao().observeSports(),
        database.trainingDao().observeAllTrainingBundles(),
        StatisticsMapper::overview,
    )

    fun observeTrainingsForSport(sportId: Int): Flow<List<TrainingSessionUiModel>> =
        database.trainingDao().observeTrainingBundlesBySport(sportId).map { it.map(::mapTrainingBundle) }

    fun observeSportStatistics(sportId: Int): Flow<SportStatisticsUiModel?> = combine(
        database.referenceDao().observeSports(),
        database.trainingDao().observeTrainingBundlesBySport(sportId),
    ) { sports, bundles -> sports.firstOrNull { it.id == sportId }?.let { StatisticsMapper.sport(it, bundles) } }

    fun observeSportsCenters(): Flow<List<SportsCenterUiModel>> =
        database.referenceDao().observeComplexesWithSports().map { centers ->
            centers.map { item ->
                SportsCenterUiModel(
                    id = item.complex.id,
                    name = item.complex.name,
                    city = item.complex.city,
                    sports = item.sports.sortedBy { it.title }.map {
                        SportSummaryUiModel(it.id, it.slug, it.title, 0)
                    },
                )
            }
        }

    suspend fun getComplexOptionsForSport(sportId: Int): List<ComplexOptionUiModel> =
        database.referenceDao().getComplexesForSport(sportId).map { ComplexOptionUiModel(it.id, it.name, it.city) }

    suspend fun saveSportsCenter(input: SaveSportsCenterInput) = database.withTransaction {
        require(input.name.isNotBlank()) { "Введите название спортивного центра" }
        require(input.sportIds.isNotEmpty()) { "Выберите хотя бы один вид спорта" }
        val entity = SportsComplexEntity(
            id = input.id ?: 0,
            name = input.name.trim(),
            city = input.city?.trim()?.takeIf(String::isNotBlank),
        )
        val centerId = if (input.id == null) {
            database.referenceDao().insertComplex(entity)
        } else {
            requireNotNull(database.referenceDao().getComplex(input.id)) { "Спортивный центр не найден" }
            database.referenceDao().updateComplex(entity)
            input.id
        }
        database.referenceDao().deleteComplexSports(centerId)
        database.referenceDao().insertComplexSports(
            input.sportIds.map { SportsComplexSportEntity(sportsComplexId = centerId, sportId = it) },
        )
    }

    suspend fun addCompletedTraining(userId: Long, input: AddCompletedTrainingInput): Long = database.withTransaction {
        val sport = requireNotNull(database.referenceDao().getSport(input.sportId)) { "Вид спорта не найден" }
        require(database.referenceDao().getComplexesForSport(sport.id).any { it.id == input.complexId }) {
            "Выбранный спорт недоступен в этом центре"
        }
        val trainingId = database.trainingDao().insertTraining(
            TrainingEntity(userId = userId, sportId = sport.id, sportsComplexId = input.complexId, trainingDate = input.date),
        )
        when (sport.slug) {
            "football" -> {
                val details = requireNotNull(input.football) { "Заполните футбольную статистику" }
                require(listOf(details.teamGoalsScored, details.teamGoalsConceded, details.userGoalsScored, details.userAssists).all { it >= 0 })
                require(details.distanceKm == null || details.distanceKm >= BigDecimal.ZERO)
                require(details.playersPerTeam == null || details.playersPerTeam > 0)
                require(details.durationMinutes == null || details.durationMinutes > 0)
                database.trainingDao().insertFootballTraining(
                    FootballTrainingEntity(
                        trainingId, details.teamGoalsScored, details.teamGoalsConceded,
                        details.userGoalsScored, details.userAssists, details.distanceKm,
                        details.playersPerTeam, details.durationMinutes,
                    ),
                )
            }
            "climbing" -> {
                require(input.climbingRoutes.isNotEmpty()) { "Добавьте хотя бы одну трассу" }
                input.climbingRoutes.forEach { require(ClimbingDifficultyCatalog.isValid(it.routeDifficulty)) }
                database.trainingDao().insertClimbingTraining(ClimbingTrainingEntity(trainingId))
                database.trainingDao().insertClimbingRoutes(input.climbingRoutes.map {
                    ClimbingRouteEntity(
                        climbingTrainingId = trainingId,
                        workoutType = it.workoutType,
                        routeDifficulty = ClimbingDifficultyCatalog.normalize(it.routeDifficulty),
                        isCompleted = it.isCompleted,
                    )
                })
            }
            else -> error("Для вида спорта «${sport.title}» пока не реализована форма статистики")
        }
        trainingId
    }

    suspend fun getScheduleMonth(userId: Long, month: YearMonth, selectedDate: LocalDate, today: LocalDate): ScheduleMonthUiModel {
        val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val gridEnd = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val sportsById = database.referenceDao().getSports().associateBy { it.id }
        val complexesById = database.referenceDao().getAllComplexes().associateBy { it.id }
        val completed = database.trainingDao().getTrainingBundlesBetween(userId, gridStart, gridEnd)
        val planned = database.planningDao().getPlannedTrainingsBetween(userId, gridStart, gridEnd)
        val recurring = database.planningDao().getRecurrenceRulesOverlapping(userId, gridStart, gridEnd)
        val eventsByDate = mutableMapOf<LocalDate, MutableList<ScheduleEventUiModel>>()

        completed.forEach { bundle ->
            eventsByDate.getOrPut(bundle.training.trainingDate, ::mutableListOf) += ScheduleEventUiModel(
                id = "completed-${bundle.training.id}", date = bundle.training.trainingDate,
                sportId = bundle.sport.id, sportSlug = bundle.sport.slug, sportTitle = bundle.sport.title,
                complexName = bundle.complex.name, state = ScheduleEventState.COMPLETED,
                isRecurring = false, details = mapTrainingBundle(bundle).details,
            )
        }
        planned.forEach { item ->
            val sport = sportsById[item.sportId] ?: return@forEach
            val center = complexesById[item.sportsComplexId] ?: return@forEach
            eventsByDate.getOrPut(item.plannedDate, ::mutableListOf) += ScheduleEventUiModel(
                "planned-${item.id}", item.plannedDate, sport.id, sport.slug, sport.title,
                center.name, ScheduleEventState.PLANNED, item.recurrenceRuleId != null,
            )
        }
        recurring.forEach { rule ->
            val sport = sportsById[rule.sportId] ?: return@forEach
            val center = complexesById[rule.sportsComplexId] ?: return@forEach
            expandRecurringDates(rule, gridStart, gridEnd).forEach { date ->
                eventsByDate.getOrPut(date, ::mutableListOf) += ScheduleEventUiModel(
                    "rule-${rule.id}-$date", date, sport.id, sport.slug, sport.title,
                    center.name, ScheduleEventState.PLANNED, true,
                )
            }
        }
        val comparator = compareBy<ScheduleEventUiModel>({ it.state }, { it.sportTitle }, { it.complexName })
        val days = generateSequence(gridStart) { it.plusDays(1).takeIf { next -> next <= gridEnd } }.map { date ->
            ScheduleDayUiModel(
                date, date.month == month.month, date == today,
                eventsByDate[date].orEmpty().sortedWith(comparator),
            )
        }.toList()
        return ScheduleMonthUiModel(
            month, selectedDate, days,
            eventsByDate[selectedDate].orEmpty().sortedWith(comparator),
        )
    }

    suspend fun addPlannedTraining(userId: Long, input: AddPlannedTrainingInput) {
        if (input.repeatWeekly) {
            database.planningDao().insertRecurrenceRule(
                RecurrenceRuleEntity(
                    userId = userId, sportId = input.sportId, sportsComplexId = input.complexId,
                    startDate = input.date, endDate = input.endDate, frequency = RecurrenceFrequency.WEEKLY,
                    intervalWeeks = input.intervalWeeks.coerceAtLeast(1),
                ),
            )
        } else {
            database.planningDao().insertPlannedTraining(
                PlannedTrainingEntity(
                    userId = userId, sportId = input.sportId, sportsComplexId = input.complexId,
                    plannedDate = input.date, status = PlannedTrainingStatus.PLANNED,
                ),
            )
        }
    }

    suspend fun createBackup(): String = database.withTransaction {
        val sports = database.referenceDao().getSports()
        val sportsById = sports.associateBy { it.id }
        val centers = database.referenceDao().getComplexesWithSports()
        val trainings = database.trainingDao().getAllTrainingBundles()
        val centerById = centers.associateBy { it.complex.id }
        val document = BackupDocument(
            exportedAt = Instant.now().toString(),
            sports = sports.map { SportBackup(it.slug, it.title) },
            centers = centers.map { item ->
                CenterBackup(item.complex.id, item.complex.name, item.complex.city, item.sports.map { it.slug })
            },
            trainings = trainings.map { bundle ->
                TrainingBackup(
                    legacyId = bundle.training.id,
                    date = bundle.training.trainingDate.toString(),
                    sportSlug = bundle.sport.slug,
                    centerLegacyId = bundle.complex.id,
                    centerName = bundle.complex.name,
                    centerCity = bundle.complex.city,
                    football = bundle.football?.let {
                        FootballBackup(
                            it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored, it.userAssists,
                            it.distanceKm?.toPlainString(), it.playersPerTeam, it.durationMinutes,
                        )
                    },
                    climbingRoutes = bundle.climbing?.routes.orEmpty().map {
                        ClimbingRouteBackup(it.workoutType.storageValue, it.routeDifficulty, it.isCompleted, it.repeatCount)
                    },
                )
            },
            plannedTrainings = database.planningDao().getAllPlannedTrainings().mapNotNull { item ->
                val sport = sportsById[item.sportId] ?: return@mapNotNull null
                val center = centerById[item.sportsComplexId]?.complex ?: return@mapNotNull null
                PlannedTrainingBackup(item.plannedDate.toString(), sport.slug, center.name, center.city, item.status.storageValue)
            },
            recurrenceRules = database.planningDao().getAllRecurrenceRules().mapNotNull { item ->
                val sport = sportsById[item.sportId] ?: return@mapNotNull null
                val center = centerById[item.sportsComplexId]?.complex ?: return@mapNotNull null
                RecurrenceRuleBackup(
                    item.startDate.toString(), item.endDate?.toString(), sport.slug,
                    center.name, center.city, item.intervalWeeks,
                )
            },
        )
        BackupCodec.encode(document)
    }

    suspend fun importData(bytes: ByteArray, userId: Long): ImportResult {
        val raw = BackupCodec.decodeText(bytes).trim()
        require(raw.isNotEmpty()) { "Файл пуст" }
        return if (raw.startsWith("{")) importBackup(BackupCodec.decode(raw), userId) else importFootballCsv(raw, userId)
    }

    private suspend fun importFootballCsv(raw: String, userId: Long): ImportResult = database.withTransaction {
        val rows = FootballCsvParser.parse(raw)
        val sport = requireNotNull(database.referenceDao().getSportBySlug("football")) { "Справочник футбола не найден" }
        val centers = database.referenceDao().getAllComplexes().associateBy { it.id }
        val existing = database.trainingDao().getAllTrainingBundles().map(::fingerprint).toMutableSet()
        var imported = 0
        var skipped = 0
        rows.forEachIndexed { index, row ->
            val center = centers[row.sportsComplexId]
                ?: error("Строка ${index + 2}: спортивный центр id=${row.sportsComplexId} не найден")
            val backup = TrainingBackup(
                date = row.trainingDate.toString(), sportSlug = sport.slug,
                centerLegacyId = center.id, centerName = center.name, centerCity = center.city,
                football = FootballBackup(
                    row.teamGoalsScored, row.teamGoalsConceded, row.userGoalsScored,
                    row.userAssists, row.distanceKm?.toPlainString(),
                ),
            )
            if (!existing.add(fingerprint(backup))) {
                skipped++
            } else {
                insertBackupTraining(backup, userId, sport, center)
                imported++
            }
        }
        ImportResult(imported, skipped, 0, "CSV Telegram-бота")
    }

    private suspend fun importBackup(document: BackupDocument, userId: Long): ImportResult = database.withTransaction {
        val referenceDao = database.referenceDao()
        val sports = referenceDao.getSports().associateBy { it.slug }.toMutableMap()
        document.sports.forEach { backup ->
            if (backup.slug !in sports) {
                referenceDao.insertSports(listOf(SportEntity(slug = backup.slug, title = backup.title)))
                sports[backup.slug] = requireNotNull(referenceDao.getSportBySlug(backup.slug))
            }
        }
        val centers = referenceDao.getAllComplexes().associateBy { centerKey(it.name, it.city) }.toMutableMap()
        var importedCenters = 0
        document.centers.forEach { backup ->
            val key = centerKey(backup.name, backup.city)
            val center = centers[key] ?: SportsComplexEntity(
                id = referenceDao.insertComplex(SportsComplexEntity(name = backup.name, city = backup.city)),
                name = backup.name, city = backup.city,
            ).also { centers[key] = it; importedCenters++ }
            val currentLinks = referenceDao.getComplexSports().filter { it.sportsComplexId == center.id }.map { it.sportId }.toSet()
            val newIds = backup.sportSlugs.mapNotNull(sports::get).map { it.id }.filterNot(currentLinks::contains)
            if (newIds.isNotEmpty()) referenceDao.insertComplexSports(newIds.map { SportsComplexSportEntity(sportsComplexId = center.id, sportId = it) })
        }
        val existing = database.trainingDao().getAllTrainingBundles().map(::fingerprint).toMutableSet()
        var imported = 0
        var skipped = 0
        document.trainings.forEach { backup ->
            val sport = requireNotNull(sports[backup.sportSlug]) { "Неизвестный вид спорта: ${backup.sportSlug}" }
            val center = centers[centerKey(backup.centerName, backup.centerCity)]
                ?: error("Спортивный центр «${backup.centerName}» отсутствует в копии")
            if (!existing.add(fingerprint(backup))) skipped++ else {
                insertBackupTraining(backup, userId, sport, center)
                imported++
            }
        }
        importPlans(document, userId, sports, centers)
        ImportResult(imported, skipped, importedCenters, "резервная копия JSON")
    }

    private suspend fun insertBackupTraining(backup: TrainingBackup, userId: Long, sport: SportEntity, center: SportsComplexEntity) {
        val id = database.trainingDao().insertTraining(
            TrainingEntity(userId = userId, sportId = sport.id, sportsComplexId = center.id, trainingDate = LocalDate.parse(backup.date)),
        )
        backup.football?.let {
            database.trainingDao().insertFootballTraining(
                FootballTrainingEntity(
                    id, it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored,
                    it.userAssists, it.distanceKm?.toBigDecimal(), it.playersPerTeam, it.durationMinutes,
                ),
            )
        }
        if (backup.climbingRoutes.isNotEmpty()) {
            database.trainingDao().insertClimbingTraining(ClimbingTrainingEntity(id))
            database.trainingDao().insertClimbingRoutes(backup.climbingRoutes.map {
                ClimbingRouteEntity(
                    climbingTrainingId = id,
                    workoutType = ClimbingWorkoutType.fromStorage(it.workoutType),
                    routeDifficulty = ClimbingDifficultyCatalog.normalize(it.routeDifficulty),
                    isCompleted = it.completed,
                    repeatCount = it.repeatCount.coerceAtLeast(1),
                )
            })
        }
    }

    private suspend fun importPlans(
        document: BackupDocument,
        userId: Long,
        sports: Map<String, SportEntity>,
        centers: Map<String, SportsComplexEntity>,
    ) {
        val existingPlans = database.planningDao().getAllPlannedTrainings().map { "${it.sportId}|${it.sportsComplexId}|${it.plannedDate}" }.toSet()
        document.plannedTrainings.forEach { backup ->
            val sport = sports[backup.sportSlug] ?: return@forEach
            val center = centers[centerKey(backup.centerName, backup.centerCity)] ?: return@forEach
            val key = "${sport.id}|${center.id}|${backup.date}"
            if (key !in existingPlans) database.planningDao().insertPlannedTraining(
                PlannedTrainingEntity(
                    userId = userId, sportId = sport.id, sportsComplexId = center.id,
                    plannedDate = LocalDate.parse(backup.date),
                    status = PlannedTrainingStatus.fromStorage(backup.status),
                ),
            )
        }
        val existingRules = database.planningDao().getAllRecurrenceRules().map { "${it.sportId}|${it.sportsComplexId}|${it.startDate}|${it.endDate}" }.toSet()
        document.recurrenceRules.forEach { backup ->
            val sport = sports[backup.sportSlug] ?: return@forEach
            val center = centers[centerKey(backup.centerName, backup.centerCity)] ?: return@forEach
            val key = "${sport.id}|${center.id}|${backup.startDate}|${backup.endDate}"
            if (key !in existingRules) database.planningDao().insertRecurrenceRule(
                RecurrenceRuleEntity(
                    userId = userId, sportId = sport.id, sportsComplexId = center.id,
                    startDate = LocalDate.parse(backup.startDate), endDate = backup.endDate?.let(LocalDate::parse),
                    frequency = RecurrenceFrequency.WEEKLY, intervalWeeks = backup.intervalWeeks.coerceAtLeast(1),
                ),
            )
        }
    }

    private fun mapTrainingBundle(bundle: TrainingBundle): TrainingSessionUiModel = TrainingSessionUiModel(
        id = bundle.training.id,
        sportSlug = bundle.sport.slug,
        sportTitle = bundle.sport.title,
        complexTitle = listOfNotNull(bundle.complex.name, bundle.complex.city).joinToString(", "),
        date = bundle.training.trainingDate,
        details = trainingDetails(bundle),
    )

    private fun trainingDetails(bundle: TrainingBundle): List<String> = buildList {
        bundle.football?.let {
            add("Счёт: ${it.teamGoalsScored}:${it.teamGoalsConceded}")
            add("Личные голы: ${it.userGoalsScored}")
            add("Голевые передачи: ${it.userAssists}")
            it.distanceKm?.let { value -> add("Дистанция: ${value.stripTrailingZeros().toPlainString()} км") }
            it.playersPerTeam?.let { value -> add("Игроков в команде: $value") }
            it.durationMinutes?.let { value -> add("Время игры: $value мин") }
        }
        bundle.climbing?.routes?.forEach {
            val count = if (it.repeatCount > 1) " × ${it.repeatCount}" else ""
            add("${it.workoutType.title} · ${it.routeDifficulty}$count · ${if (it.isCompleted) "пройдена" else "не пройдена"}")
        }
    }

    private fun fingerprint(bundle: TrainingBundle): String = fingerprint(
        TrainingBackup(
            date = bundle.training.trainingDate.toString(), sportSlug = bundle.sport.slug,
            centerName = bundle.complex.name, centerCity = bundle.complex.city,
            football = bundle.football?.let {
                FootballBackup(
                    it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored, it.userAssists,
                    it.distanceKm?.stripTrailingZeros()?.toPlainString(), it.playersPerTeam, it.durationMinutes,
                )
            },
            climbingRoutes = bundle.climbing?.routes.orEmpty().map {
                ClimbingRouteBackup(it.workoutType.storageValue, it.routeDifficulty, it.isCompleted, it.repeatCount)
            },
        ),
    )

    private fun fingerprint(item: TrainingBackup): String = listOf(
        item.date, item.sportSlug, centerKey(item.centerName, item.centerCity),
        item.football?.let {
            listOf(it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored, it.userAssists, it.distanceKm?.toBigDecimalOrNull()?.stripTrailingZeros(), it.playersPerTeam, it.durationMinutes).joinToString(":")
        }.orEmpty(),
        item.climbingRoutes.sortedWith(compareBy({ it.workoutType }, { it.routeDifficulty }, { it.completed }, { it.repeatCount })).joinToString(";") {
            "${it.workoutType}:${it.routeDifficulty}:${it.completed}:${it.repeatCount}"
        },
    ).joinToString("|")

    private fun centerKey(name: String, city: String?): String = "${name.trim().lowercase()}|${city.orEmpty().trim().lowercase()}"

    private fun expandRecurringDates(rule: RecurrenceRuleEntity, rangeStart: LocalDate, rangeEnd: LocalDate): List<LocalDate> {
        if (rule.frequency != RecurrenceFrequency.WEEKLY) return emptyList()
        val hardEnd = minOf(rangeEnd, rule.endDate ?: rangeEnd)
        if (hardEnd < rangeStart) return emptyList()
        val stepDays = 7L * rule.intervalWeeks.coerceAtLeast(1)
        var cursor = rule.startDate
        if (cursor < rangeStart) {
            val difference = java.time.temporal.ChronoUnit.DAYS.between(cursor, rangeStart)
            cursor = cursor.plusDays((difference / stepDays) * stepDays)
            while (cursor < rangeStart) cursor = cursor.plusDays(stepDays)
        }
        return buildList {
            while (cursor <= hardEnd) {
                add(cursor)
                cursor = cursor.plusDays(stepDays)
            }
        }
    }
}
