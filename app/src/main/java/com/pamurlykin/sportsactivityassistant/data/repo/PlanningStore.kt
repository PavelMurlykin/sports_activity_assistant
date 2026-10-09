package com.pamurlykin.sportsactivityassistant.data.repo

import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.entity.*
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules
import java.time.LocalDate
import java.util.UUID

/** Called only within the repository transaction. Virtual events acquire a persistent identity on first change. */
internal class PlanningStore(private val db: AppDatabase, private val owner: Long) {
    private val dao get() = db.planningDao()
    private data class Event(val plan: PlannedTrainingEntity, val rule: RecurrenceRuleEntity?)

    private suspend fun event(key: String, series: Boolean = false): Event {
        val plan = if (key.startsWith("planned-")) {
            requireNotNull(dao.getPlan(key.removePrefix("planned-").toLong())) { "План не найден" }
        } else {
            val match = requireNotNull(Regex("rule-(\\d+)-(\\d{4}-\\d{2}-\\d{2})").matchEntire(key)) { "План не найден" }
            val rule = requireNotNull(dao.getRule(match.groupValues[1].toLong())) { "Серия не найдена" }
            val date = TrainingValidation.parseDate(match.groupValues[2])
            dao.getOccurrence(rule.id, date) ?: run {
                require(series || rule.frequency == RecurrenceFrequency.WEEKLY &&
                    ScheduleDates.occurs(rule.startDate, rule.endDate, rule.intervalWeeks, date)) { "Серия изменилась: событие больше не существует" }
                PlannedTrainingEntity(userId = rule.userId, sportId = rule.sportId, sportsComplexId = rule.sportsComplexId,
                    plannedDate = date, recurrenceRuleId = rule.id, occurrenceDate = date,
                    createdAt = rule.createdAt, publicId = UUID.nameUUIDFromBytes("occurrence:${rule.publicId}:$date".toByteArray()).toString())
            }
        }
        require(plan.userId == owner) { "План принадлежит другому локальному профилю" }
        val rule = plan.recurrenceRuleId?.let { requireNotNull(dao.getRule(it)) }
        require(rule == null || rule.userId == owner) { "Серия принадлежит другому локальному профилю" }
        return Event(plan, rule)
    }

    private fun snapshot(key: String, e: Event, scope: PlanScope): PlanSnapshot {
        val rule = e.rule.takeIf { scope == PlanScope.SERIES }
        if (scope == PlanScope.SERIES) require(rule != null) { "У разового плана нет серии" }
        return PlanSnapshot(key, rule?.sportId ?: e.plan.sportId, rule?.sportsComplexId ?: e.plan.sportsComplexId,
            (rule?.startDate ?: e.plan.plannedDate).toString(), rule != null,
            rule?.intervalWeeks ?: 1, rule?.endDate?.toString(),
            e.plan.toString() + "|" + e.rule.toString(),
            e.plan.status == PlannedTrainingStatus.CANCELED || e.rule?.isCanceled == true)
    }

    suspend fun load(key: String, scope: PlanScope) = snapshot(key, event(key, scope == PlanScope.SERIES), scope)

    private suspend fun persist(plan: PlannedTrainingEntity) {
        if (plan.id == 0L) dao.insertPlannedTraining(plan) else dao.updatePlan(plan)
    }

    private suspend fun validate(input: AddPlannedTrainingInput, oldSport: Int? = null, oldCenter: Long? = null) {
        TrainingValidation.recurrence(input.date, input.endDate, input.intervalWeeks)
        SportModules.require(requireNotNull(db.referenceDao().getSport(input.sportId)) { "Вид спорта не найден" }.slug)
        if (input.sportId != oldSport || input.complexId != oldCenter) {
            require(db.referenceDao().getComplexesForSport(input.sportId).any { it.id == input.complexId }) {
                "Выбранный спорт недоступен в этом центре"
            }
        }
    }

    suspend fun create(input: AddPlannedTrainingInput, requestId: String): Long {
        require(UUID.fromString(requestId).toString() == requestId) { "Некорректный идентификатор плана" }
        dao.getAllPlannedTrainings().firstOrNull { it.publicId == requestId }?.let {
            require(!input.repeatWeekly && it.userId == owner && it.sportId == input.sportId &&
                it.sportsComplexId == input.complexId && it.plannedDate == input.date) { "Идентификатор уже занят" }
            return it.id
        }
        dao.getAllRecurrenceRules().firstOrNull { it.publicId == requestId }?.let {
            require(input.repeatWeekly && it.userId == owner && it.sportId == input.sportId && it.sportsComplexId == input.complexId &&
                it.startDate == input.date && it.endDate == input.endDate && it.intervalWeeks == input.intervalWeeks) { "Идентификатор уже занят" }
            return it.id
        }
        validate(input)
        require(db.referenceDao().getUsers().any { it.id == owner }) { "Локальный профиль не найден" }
        return if (input.repeatWeekly) dao.insertRecurrenceRule(RecurrenceRuleEntity(userId = owner,
            sportId = input.sportId, sportsComplexId = input.complexId, startDate = input.date, endDate = input.endDate,
            frequency = RecurrenceFrequency.WEEKLY, intervalWeeks = input.intervalWeeks, publicId = requestId))
        else dao.insertPlannedTraining(PlannedTrainingEntity(userId = owner, sportId = input.sportId,
            sportsComplexId = input.complexId, plannedDate = input.date, publicId = requestId))
    }

    suspend fun update(old: PlanSnapshot, scope: PlanScope, input: AddPlannedTrainingInput) {
        val e = event(old.key, scope == PlanScope.SERIES)
        val current = snapshot(old.key, e, scope)
        // An identical retry is harmless after rotation/process restoration.
        if (current.input() == input) return
        require(current.revision == old.revision) { "План изменился. Закройте форму и откройте его заново." }
        if (scope == PlanScope.EVENT) require(e.plan.completedTrainingId == null) { "Изменяйте связанный результат, а не завершённый план" }
        require(input.repeatWeekly == (scope == PlanScope.SERIES)) { "Разовую тренировку нельзя превратить в серию при редактировании" }
        validate(input, current.sportId, current.complexId)
        if (scope == PlanScope.SERIES) {
            dao.updateRule(requireNotNull(e.rule).copy(sportId = input.sportId, sportsComplexId = input.complexId,
                startDate = input.date, endDate = input.endDate, intervalWeeks = input.intervalWeeks))
        } else persist(e.plan.copy(sportId = input.sportId, sportsComplexId = input.complexId, plannedDate = input.date,
            occurrenceDate = if (e.rule != null) e.plan.occurrenceDate ?: e.plan.plannedDate else null))
    }

    suspend fun cancel(old: PlanSnapshot, scope: PlanScope) {
        val e = event(old.key, scope == PlanScope.SERIES)
        if (scope == PlanScope.SERIES && e.rule?.isCanceled == true ||
            scope == PlanScope.EVENT && e.plan.status == PlannedTrainingStatus.CANCELED) return
        require(snapshot(old.key, e, scope).revision == old.revision) { "План изменился. Откройте подтверждение заново." }
        if (scope == PlanScope.SERIES) dao.updateRule(requireNotNull(e.rule).copy(isCanceled = true))
        else {
            require(e.plan.completedTrainingId == null) { "Завершённый план отменить нельзя" }
            persist(e.plan.copy(status = PlannedTrainingStatus.CANCELED))
        }
    }

    suspend fun complete(old: PlanSnapshot, input: AddCompletedTrainingInput, requestId: String,
        save: suspend () -> Long): Long {
        val e = event(old.key)
        e.plan.completedTrainingId?.let { id ->
            val result = requireNotNull(db.trainingDao().getTrainingBundle(id))
            require(result.training.publicId == requestId) { "Для этого плана уже записан результат" }
            return save() // also verifies identical payload of a retried request
        }
        require(snapshot(old.key, e, PlanScope.EVENT).revision == old.revision) { "План изменился. Откройте его заново." }
        require(!old.canceled && !snapshot(old.key, e, PlanScope.EVENT).canceled) { "Отменённую тренировку завершить нельзя" }
        require(input.sportId == e.plan.sportId) { "Вид спорта должен соответствовать плану" }
        val id = save()
        require(dao.getPlanForResult(id) == null) { "Результат уже связан с другим планом" }
        persist(e.plan.copy(status = PlannedTrainingStatus.COMPLETED, completedTrainingId = id))
        return id
    }
}
