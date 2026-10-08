package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.withTransaction
import com.pamurlykin.sportsactivityassistant.data.backup.*
import com.pamurlykin.sportsactivityassistant.data.model.TrainingValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.entity.PlannedTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.RecurrenceRuleEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexSportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingEntity
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.AddPlannedTrainingInput
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.pamurlykin.sportsactivityassistant.data.seed.LocalSeed
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules

class AppRepository(private val database: AppDatabase) {
    private val initializationLock = Mutex()
    private var profileId: Long? = null

    suspend fun localProfileId(): Long = initializationLock.withLock {
        profileId ?: LocalSeed.initialize(database).also { profileId = it }
    }

    private fun <T> readyFlow(source: () -> Flow<T>): Flow<T> = flow {
        localProfileId()
        emitAll(source())
    }

    private suspend fun <T> readyTransaction(block: suspend () -> T): T {
        localProfileId()
        return database.withTransaction(block)
    }

    fun observeStatisticsOverview(): Flow<StatisticsOverviewUiModel> = readyFlow { combine(
        database.referenceDao().observeSports(),
        database.trainingDao().observeAllTrainingBundles(),
    ) { sports, bundles -> StatisticsMapper.overview(sports, bundles.filter { it.training.userId == profileId }) }
    }

    fun observeTrainingsForSport(sportId: Int): Flow<List<TrainingSessionUiModel>> =
        readyFlow { database.trainingDao().observeTrainingBundlesBySport(sportId).map { it.filter { bundle -> bundle.training.userId == profileId }.map(::mapTrainingBundle) } }

    fun observeSportStatistics(sportId: Int): Flow<SportStatisticsUiModel?> = readyFlow { combine(
        database.referenceDao().observeSports(),
        database.trainingDao().observeTrainingBundlesBySport(sportId),
    ) { sports, bundles -> sports.firstOrNull { it.id == sportId }?.let { StatisticsMapper.sport(it, bundles.filter { bundle -> bundle.training.userId == profileId }) } } }

    fun observeSportsCenters(): Flow<List<SportsCenterUiModel>> =
        readyFlow { database.referenceDao().observeComplexesWithSports().map { centers ->
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
        } }

    suspend fun getComplexOptionsForSport(sportId: Int): List<ComplexOptionUiModel> = readyTransaction {
        database.referenceDao().getComplexesForSport(sportId).map { ComplexOptionUiModel(it.id, it.name, it.city) }
    }

    suspend fun saveSportsCenter(input: SaveSportsCenterInput) = readyTransaction {
        require(input.name.isNotBlank()) { "Введите название спортивного центра" }
        require(input.sportIds.isNotEmpty()) { "Выберите хотя бы один вид спорта" }
        input.sportIds.forEach { id -> SportModules.require(requireNotNull(database.referenceDao().getSport(id)) { "Вид спорта не найден" }.slug) }
        val existing = input.id?.let { requireNotNull(database.referenceDao().getComplex(it)) { "Спортивный центр не найден" } }
        val entity = (existing ?: SportsComplexEntity(name = input.name, city = input.city)).copy(
            name = input.name.trim(), city = input.city?.trim()?.takeIf(String::isNotBlank), isInitial = false,
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

    suspend fun addCompletedTraining(userId: Long, input: AddCompletedTrainingInput): Long = readyTransaction {
        TrainingValidation.date(input.date)
        val sport = requireNotNull(database.referenceDao().getSport(input.sportId)) { "Вид спорта не найден" }
        require(database.referenceDao().getComplexesForSport(sport.id).any { it.id == input.complexId }) {
            "Выбранный спорт недоступен в этом центре"
        }
        require(database.referenceDao().getUsers().any { it.id == userId }) { "Локальный профиль не найден" }
        val module = SportModules.require(sport.slug)
        module.validate(input)
        val trainingId = database.trainingDao().insertTraining(
            TrainingEntity(userId = userId, sportId = sport.id, sportsComplexId = input.complexId, trainingDate = input.date),
        )
        module.insertDetails(database.trainingDao(), trainingId, input)
        trainingId
    }

    suspend fun getScheduleMonth(userId: Long, month: YearMonth, selectedDate: LocalDate, today: LocalDate): ScheduleMonthUiModel = readyTransaction {
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
        ScheduleMonthUiModel(
            month, selectedDate, days,
            eventsByDate[selectedDate].orEmpty().sortedWith(comparator),
        )
    }

    suspend fun addPlannedTraining(userId: Long, input: AddPlannedTrainingInput) = readyTransaction {
        val sport = requireNotNull(database.referenceDao().getSport(input.sportId)) { "Вид спорта не найден" }
        SportModules.require(sport.slug)
        require(database.referenceDao().getComplexesForSport(sport.id).any { it.id == input.complexId }) { "Выбранный спорт недоступен в этом центре" }
        require(database.referenceDao().getUsers().any { it.id == userId }) { "Локальный профиль не найден" }
        TrainingValidation.recurrence(input.date, input.endDate, input.intervalWeeks)
        if (input.repeatWeekly) {
            database.planningDao().insertRecurrenceRule(
                RecurrenceRuleEntity(
                    userId = userId, sportId = input.sportId, sportsComplexId = input.complexId,
                    startDate = input.date, endDate = input.endDate, frequency = RecurrenceFrequency.WEEKLY,
                    intervalWeeks = input.intervalWeeks,
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

    suspend fun createBackup(): String = withContext(Dispatchers.IO) {
        readyTransaction {
            val document = DataExchange(database).snapshot(localProfileId())
            require(document.objectCount() <= ImportFiles.MAX_ITEMS) { "Копия превышает поддерживаемый лимит 100000 объектов" }
            BackupCodec.encode(document).also { require(it.toByteArray(Charsets.UTF_8).size <= ImportFiles.MAX_BYTES) { "Копия превышает поддерживаемый лимит 16 МиБ" } }
        }
    }

    suspend fun prepareImport(bytes: ByteArray): ParsedImport = withContext(Dispatchers.Default) { ImportParser.parse(bytes) }

    suspend fun previewImport(source: ParsedImport, choices: ImportChoices? = null): ImportPreview = withContext(Dispatchers.IO) {
        readyTransaction { DataExchange(database).preview(source, choices, localProfileId()) }
    }

    suspend fun applyImport(source: ParsedImport, choices: ImportChoices): ImportResult = withContext(Dispatchers.IO) {
        readyTransaction { DataExchange(database).apply(source, choices, localProfileId()) }
    }

    /** Non-UI clients must provide explicit choices when defaults cannot safely resolve a file. */
    suspend fun importData(bytes: ByteArray, userId: Long): ImportResult {
        require(userId == localProfileId()) { "Импорт выполняется через текущий локальный профиль" }
        val source = prepareImport(bytes)
        return withContext(Dispatchers.IO) {
            readyTransaction { DataExchange(database).apply(source, null, userId) }
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

    private fun trainingDetails(bundle: TrainingBundle): List<String> =
        SportModules.find(bundle.sport.slug)?.details(bundle)
            ?: listOf("Историческая запись: этот вид спорта не поддерживается текущей версией")

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
