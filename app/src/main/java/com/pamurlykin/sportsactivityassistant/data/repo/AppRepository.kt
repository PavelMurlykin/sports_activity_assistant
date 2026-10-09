package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.withTransaction
import com.pamurlykin.sportsactivityassistant.data.backup.*
import com.pamurlykin.sportsactivityassistant.data.model.CenterNames
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
import com.pamurlykin.sportsactivityassistant.data.model.TrainingEditSnapshot
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import com.pamurlykin.sportsactivityassistant.data.model.*
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

    private fun observeStatisticsChanges() = database.invalidationTracker.createFlow(
        "trainings", "football_trainings", "climbing_routes", "sports", "sports_complexes",
    )

    fun observeStatisticsOverview(filter: StatisticsFilter = StatisticsFilter()): Flow<StatisticsOverviewUiModel> =
        readyFlow { observeStatisticsChanges().map {
            readyTransaction {
                val user = localProfileId()
                val dao = database.trainingDao()
                val sports = database.referenceDao().getSports()
                val counts = dao.sportCounts(user, filter.firstDate, filter.lastDate, filter.centerId)
                val highlights = sports.associate { sport ->
                    sport.id to SportModules.find(sport.slug)?.aggregate(dao, StatisticsSelection(user, sport.id, filter), includeMetrics = false)?.highlights.orEmpty()
                }
                StatisticsMapper.aggregatedOverview(sports, counts,
                    dao.monthCounts(user, filter.firstDate, filter.lastDate, filter.centerId), highlights, filter)
            }
        } }.flowOn(Dispatchers.IO)

    /** Compatibility API: the first bounded page, not the entire training history. */
    fun observeTrainingsForSport(sportId: Int): Flow<List<TrainingSessionUiModel>> =
        observeTrainingPage(sportId).map { it.items }

    fun observeTrainingPage(sportId: Int, filter: StatisticsFilter = StatisticsFilter(), page: Int = 0): Flow<TrainingPageUiModel> {
        require(page >= 0) { "Некорректная страница" }
        return readyFlow { observeStatisticsChanges().map {
            readyTransaction {
                val dao = database.trainingDao()
                val user = localProfileId()
                val total = dao.trainingCount(user, sportId, filter.firstDate, filter.lastDate, filter.centerId)
                val pageCount = ((total.toLong() + TrainingPageUiModel.SIZE - 1) / TrainingPageUiModel.SIZE).toInt().coerceAtLeast(1)
                val actual = page.coerceAtMost(pageCount - 1)
                TrainingPageUiModel(dao.trainingPage(user, sportId, filter.firstDate, filter.lastDate,
                    filter.centerId, TrainingPageUiModel.SIZE, actual * TrainingPageUiModel.SIZE).map(::mapTrainingBundle), actual, total, filter)
            }
        } }.flowOn(Dispatchers.IO)
    }

    fun observeSportStatistics(sportId: Int, filter: StatisticsFilter = StatisticsFilter()): Flow<SportStatisticsUiModel?> =
        readyFlow { observeStatisticsChanges().map {
            readyTransaction {
                database.referenceDao().getSport(sportId)?.let { sport ->
                    val module = SportModules.find(sport.slug)
                    val selection = StatisticsSelection(localProfileId(), sport.id, filter)
                    val metrics = module?.aggregate(database.trainingDao(), selection)?.metrics ?: listOf(
                        MetricUiModel("Исторические тренировки", database.trainingDao().trainingCount(
                            selection.userId, sportId, filter.firstDate, filter.lastDate, filter.centerId).toString()),
                        MetricUiModel("Статистика", "Вид спорта не поддерживается"),
                    )
                    SportStatisticsUiModel(sport.id, sport.slug, sport.title, metrics, filter)
                }
            }
        } }.flowOn(Dispatchers.IO)

    fun observeSportsCenters(): Flow<List<SportsCenterUiModel>> =
        readyFlow { database.referenceDao().observeComplexesWithSports().map { centers ->
            centers.map { item ->
                SportsCenterUiModel(
                    id = item.complex.id,
                    name = item.complex.name,
                    city = item.complex.city,
                    isArchived = item.complex.isArchived,
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
        val name = CenterNames.clean(input.name)
        val city = CenterNames.clean(input.city.orEmpty()).takeIf(String::isNotBlank)
        CenterNames.validate(name, city)
        require(input.sportIds.isNotEmpty()) { "Выберите хотя бы один вид спорта" }
        input.sportIds.forEach { id -> SportModules.require(requireNotNull(database.referenceDao().getSport(id)) { "Вид спорта не найден" }.slug) }
        val existing = input.id?.let { requireNotNull(database.referenceDao().getComplex(it)) { "Спортивный центр не найден" } }
        val key = CenterNames.key(name, city)
        // An unchanged historical duplicate can still be edited; do not force a merge.
        val duplicates = database.referenceDao().getAllComplexes().filter { it.id != input.id && CenterNames.key(it.name, it.city) == key }
        require(duplicates.isEmpty() || existing != null && CenterNames.key(existing.name, existing.city) == key) {
            "Центр с таким названием и городом уже есть, в том числе в архиве. Измените существующий центр."
        }
        val entity = (existing ?: SportsComplexEntity(name = name, city = city)).copy(
            name = if (existing != null && duplicates.isNotEmpty()) existing.name else name,
            city = if (existing != null && duplicates.isNotEmpty()) existing.city else city, isInitial = false,
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
        centerId
    }

    suspend fun setSportsCenterArchived(id: Long, archived: Boolean) = readyTransaction {
        val center = requireNotNull(database.referenceDao().getComplex(id)) { "Спортивный центр не найден" }
        database.referenceDao().updateComplex(center.copy(isArchived = archived, isInitial = false))
    }

    suspend fun addCompletedTraining(userId: Long, input: AddCompletedTrainingInput, requestId: String? = null): Long = readyTransaction {
        if (requestId != null) {
            require(java.util.UUID.fromString(requestId).toString() == requestId) { "Некорректный идентификатор записи" }
            database.trainingDao().getTrainingByPublicId(requestId)?.let { existing ->
                require(existing.training.userId == userId && sameInput(editSnapshot(existing).input, input)) {
                    "Идентификатор записи уже занят другой тренировкой"
                }
                return@readyTransaction existing.training.id
            }
        }
        TrainingValidation.completedDate(input.date)
        val sport = requireNotNull(database.referenceDao().getSport(input.sportId)) { "Вид спорта не найден" }
        require(database.referenceDao().getComplexesForSport(sport.id).any { it.id == input.complexId }) {
            "Выбранный спорт недоступен в этом центре"
        }
        require(database.referenceDao().getUsers().any { it.id == userId }) { "Локальный профиль не найден" }
        val module = SportModules.require(sport.slug)
        module.validate(input)
        val trainingId = database.trainingDao().insertTraining(
            TrainingEntity(userId = userId, sportId = sport.id, sportsComplexId = input.complexId, trainingDate = input.date, publicId = requestId ?: java.util.UUID.randomUUID().toString()),
        )
        module.insertDetails(database.trainingDao(), trainingId, input)
        trainingId
    }

    private fun sameInput(a: AddCompletedTrainingInput, b: AddCompletedTrainingInput): Boolean {
        fun canonical(it: AddCompletedTrainingInput) = it.copy(
            football = it.football?.let { details -> details.copy(distanceKm = details.distanceKm?.stripTrailingZeros()) },
            climbingRoutes = it.climbingRoutes.sortedBy { route -> route.publicId },
        )
        return canonical(a) == canonical(b)
    }

    private fun editSnapshot(bundle: TrainingBundle): TrainingEditSnapshot {
        val module = SportModules.require(bundle.sport.slug)
        // Reference names are deliberately excluded: renaming a center does not change a workout.
        val common = TrainingBackup(
            legacyId = bundle.training.id, date = bundle.training.trainingDate.toString(),
            sportSlug = bundle.sport.slug, centerName = "", publicId = bundle.training.publicId,
            centerPublicId = bundle.complex.publicId, createdAt = bundle.training.createdAt.toString(),
        )
        val backup = module.encodeDetails(bundle, common).let { it.copy(climbingRoutes = it.climbingRoutes.sortedBy { route -> route.publicId }) }
        val revision = Json.encodeToString(TrainingBackup.serializer(), backup)
        return TrainingEditSnapshot.restore(bundle.training.id, bundle.sport.id, bundle.complex.id, revision)
    }

    private suspend fun ownedTraining(id: Long): TrainingBundle {
        val bundle = requireNotNull(database.trainingDao().getTrainingBundle(id)) { "Тренировка уже удалена" }
        require(bundle.training.userId == localProfileId()) { "Тренировка принадлежит другому локальному профилю" }
        return bundle
    }

    suspend fun loadTrainingForEdit(id: Long): TrainingEditSnapshot = readyTransaction { editSnapshot(ownedTraining(id)) }

    suspend fun updateCompletedTraining(snapshot: TrainingEditSnapshot, input: AddCompletedTrainingInput) = readyTransaction {
        val bundle = ownedTraining(snapshot.id)
        // Retrying after process restoration must not overwrite a newer, different result.
        if (sameInput(editSnapshot(bundle).input, input)) return@readyTransaction
        require(editSnapshot(bundle).revision == snapshot.revision) { "Запись изменилась. Закройте форму и откройте тренировку заново; ваши изменения не записаны." }
        require(input.sportId == bundle.sport.id) { "Вид спорта сохранённой тренировки менять нельзя" }
        TrainingValidation.completedDate(input.date, bundle.training.trainingDate)
        if (input.complexId != bundle.complex.id) {
            require(database.referenceDao().getComplexesForSport(input.sportId).any { it.id == input.complexId }) {
                "Выбранный спорт недоступен в этом центре"
            }
        }
        val module = SportModules.require(bundle.sport.slug)
        module.validateEdit(input, editSnapshot(bundle).input)
        database.trainingDao().updateTraining(bundle.training.copy(sportsComplexId = input.complexId, trainingDate = input.date))
        module.updateDetails(database.trainingDao(), bundle, input)
    }

    suspend fun deleteCompletedTraining(snapshot: TrainingEditSnapshot) = readyTransaction {
        // A confirmed deletion resumed after process death is already successful if the row is gone.
        if (database.trainingDao().getTrainingBundle(snapshot.id) == null) return@readyTransaction
        val bundle = ownedTraining(snapshot.id)
        require(editSnapshot(bundle).revision == snapshot.revision) { "Запись изменилась. Откройте подтверждение удаления заново." }
        database.planningDao().getPlanForResult(snapshot.id)?.let { plan ->
            val ruleCanceled = plan.recurrenceRuleId?.let { database.planningDao().getRule(it)?.isCanceled } == true
            database.planningDao().updatePlan(plan.copy(completedTrainingId = null,
                status = if (ruleCanceled) PlannedTrainingStatus.CANCELED else PlannedTrainingStatus.PLANNED))
        }
        check(database.trainingDao().deleteTraining(snapshot.id) == 1) { "Тренировка уже удалена" }
    }

    suspend fun getScheduleMonth(userId: Long, month: YearMonth, selectedDate: LocalDate, today: LocalDate): ScheduleMonthUiModel = readyTransaction {
        val gridDates = ScheduleDates.grid(month)
        val gridStart = gridDates.first()
        val gridEnd = gridDates.last()
        val sportsById = database.referenceDao().getSports().associateBy { it.id }
        val complexesById = database.referenceDao().getAllComplexes().associateBy { it.id }
        val completed = database.trainingDao().getTrainingBundlesBetween(userId, gridStart, gridEnd)
        val planned = database.planningDao().getAllPlannedTrainings().filter { it.userId == userId }
        val recurring = database.planningDao().getRecurrenceRulesOverlapping(userId, gridStart, gridEnd)
        val eventsByDate = mutableMapOf<LocalDate, MutableList<ScheduleEventUiModel>>()

        completed.forEach { bundle ->
            eventsByDate.getOrPut(bundle.training.trainingDate, ::mutableListOf) += ScheduleEventUiModel(
                id = "completed-${bundle.training.id}", date = bundle.training.trainingDate,
                sportId = bundle.sport.id, sportSlug = bundle.sport.slug, sportTitle = bundle.sport.title,
                complexName = bundle.complex.name, state = ScheduleEventState.COMPLETED,
                isRecurring = planned.any { it.completedTrainingId == bundle.training.id && it.recurrenceRuleId != null },
                details = mapTrainingBundle(bundle).details,
                linkedPlan = planned.any { it.completedTrainingId == bundle.training.id },
            )
        }
        planned.filter { it.plannedDate in gridStart..gridEnd && it.completedTrainingId == null }.forEach { item ->
            val sport = sportsById[item.sportId] ?: return@forEach
            val center = complexesById[item.sportsComplexId] ?: return@forEach
            eventsByDate.getOrPut(item.plannedDate, ::mutableListOf) += ScheduleEventUiModel(
                "planned-${item.id}", item.plannedDate, sport.id, sport.slug, sport.title,
                center.name, if (item.status == PlannedTrainingStatus.CANCELED ||
                    item.recurrenceRuleId?.let { database.planningDao().getRule(it)?.isCanceled } == true)
                    ScheduleEventState.CANCELED else ScheduleEventState.PLANNED,
                item.recurrenceRuleId != null,
                details = if (item.occurrenceDate != null && item.occurrenceDate != item.plannedDate)
                    listOf("Перенесена с ${item.occurrenceDate}") else emptyList(),
                planKey = "planned-${item.id}",
            )
        }
        recurring.forEach { rule ->
            val sport = sportsById[rule.sportId] ?: return@forEach
            val center = complexesById[rule.sportsComplexId] ?: return@forEach
            (if (rule.frequency == RecurrenceFrequency.WEEKLY)
                ScheduleDates.expand(rule.startDate, rule.endDate, rule.intervalWeeks, gridStart, gridEnd) else emptyList()).forEach { date ->
                if (planned.any { it.recurrenceRuleId == rule.id && (it.occurrenceDate ?: it.plannedDate) == date }) return@forEach
                eventsByDate.getOrPut(date, ::mutableListOf) += ScheduleEventUiModel(
                    "rule-${rule.id}-$date", date, sport.id, sport.slug, sport.title,
                    center.name, if (rule.isCanceled) ScheduleEventState.CANCELED else ScheduleEventState.PLANNED, true,
                    details = listOf("Интервал: ${rule.intervalWeeks} нед."),
                    planKey = "rule-${rule.id}-$date",
                )
            }
        }
        val comparator = compareBy<ScheduleEventUiModel>({ it.state }, { it.sportTitle }, { it.complexName })
        val days = gridDates.map { date ->
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

    fun observeScheduleMonth(userId: Long, month: YearMonth, selectedDate: LocalDate, today: LocalDate): Flow<ScheduleMonthUiModel> =
        readyFlow { database.invalidationTracker.createFlow("planned_trainings", "recurrence_rules",
            "trainings", "football_trainings", "climbing_routes", "sports_complexes", "sports").map {
                getScheduleMonth(userId, month, selectedDate, today)
            } }.flowOn(Dispatchers.IO)

    suspend fun addPlannedTraining(userId: Long, input: AddPlannedTrainingInput, requestId: String = java.util.UUID.randomUUID().toString()): Long =
        readyTransaction { PlanningStore(database, userId).create(input, requestId) }

    suspend fun loadPlan(key: String, scope: PlanScope = PlanScope.EVENT) =
        readyTransaction { PlanningStore(database, localProfileId()).load(key, scope) }

    suspend fun updatePlan(snapshot: PlanSnapshot, scope: PlanScope, input: AddPlannedTrainingInput) =
        readyTransaction { PlanningStore(database, localProfileId()).update(snapshot, scope, input) }

    suspend fun cancelPlan(snapshot: PlanSnapshot, scope: PlanScope) =
        readyTransaction { PlanningStore(database, localProfileId()).cancel(snapshot, scope) }

    suspend fun completePlan(snapshot: PlanSnapshot, input: AddCompletedTrainingInput, requestId: String): Long =
        readyTransaction { PlanningStore(database, localProfileId()).complete(snapshot, input, requestId) {
            addCompletedTraining(localProfileId(), input, requestId)
        } }

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

}
