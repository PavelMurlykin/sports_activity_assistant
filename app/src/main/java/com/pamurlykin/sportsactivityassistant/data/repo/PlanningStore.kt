package com.pamurlykin.sportsactivityassistant.data.repo

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

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
            requireNotNull(dao.getPlan(key.removePrefix("planned-").toLong())) { AppText.get(R.string.planning_store_plan_ne_nayden) }
        } else {
            val match = requireNotNull(Regex("rule-(\\d+)-(\\d{4}-\\d{2}-\\d{2})").matchEntire(key)) { AppText.get(R.string.planning_store_plan_ne_nayden) }
            val rule = requireNotNull(dao.getRule(match.groupValues[1].toLong())) { AppText.get(R.string.planning_store_seriya_ne_naydena) }
            val date = TrainingValidation.parseDate(match.groupValues[2])
            dao.getOccurrence(rule.id, date) ?: run {
                require(series || rule.frequency == RecurrenceFrequency.WEEKLY &&
                    ScheduleDates.occurs(rule.startDate, rule.endDate, rule.intervalWeeks, date)) { AppText.get(R.string.planning_store_seriya_izmenilas_sobytie_bolshe_ne) }
                PlannedTrainingEntity(userId = rule.userId, sportId = rule.sportId, sportsComplexId = rule.sportsComplexId,
                    plannedDate = date, recurrenceRuleId = rule.id, occurrenceDate = date,
                    createdAt = rule.createdAt, publicId = UUID.nameUUIDFromBytes("occurrence:${rule.publicId}:${date}".toByteArray()).toString())
            }
        }
        require(plan.userId == owner) { AppText.get(R.string.planning_store_plan_prinadlezhit_drugomu_lokalnomu_profilyu) }
        val rule = plan.recurrenceRuleId?.let { requireNotNull(dao.getRule(it)) }
        require(rule == null || rule.userId == owner) { AppText.get(R.string.planning_store_seriya_prinadlezhit_drugomu_lokalnomu_profilyu) }
        return Event(plan, rule)
    }

    private fun snapshot(key: String, e: Event, scope: PlanScope): PlanSnapshot {
        val rule = e.rule.takeIf { scope == PlanScope.SERIES }
        if (scope == PlanScope.SERIES) require(rule != null) { AppText.get(R.string.planning_store_u_razovogo_plana_net_serii) }
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
        SportModules.require(requireNotNull(db.referenceDao().getSport(input.sportId)) { AppText.get(R.string.app_repository_vid_sporta_ne_nayden) }.slug)
        if (input.sportId != oldSport || input.complexId != oldCenter) {
            require(db.referenceDao().getComplexesForSport(input.sportId).any { it.id == input.complexId }) {
                AppText.get(R.string.app_repository_vybrannyy_sport_nedostupen_v_etom)
            }
        }
    }

    suspend fun create(input: AddPlannedTrainingInput, requestId: String): Long {
        require(UUID.fromString(requestId).toString() == requestId) { AppText.get(R.string.planning_store_nekorrektnyy_identifikator_plana) }
        dao.getAllPlannedTrainings().firstOrNull { it.publicId == requestId }?.let {
            require(!input.repeatWeekly && it.userId == owner && it.sportId == input.sportId &&
                it.sportsComplexId == input.complexId && it.plannedDate == input.date) { AppText.get(R.string.planning_store_identifikator_uzhe_zanyat) }
            return it.id
        }
        dao.getAllRecurrenceRules().firstOrNull { it.publicId == requestId }?.let {
            require(input.repeatWeekly && it.userId == owner && it.sportId == input.sportId && it.sportsComplexId == input.complexId &&
                it.startDate == input.date && it.endDate == input.endDate && it.intervalWeeks == input.intervalWeeks) { AppText.get(R.string.planning_store_identifikator_uzhe_zanyat) }
            return it.id
        }
        validate(input)
        require(db.referenceDao().getUsers().any { it.id == owner }) { AppText.get(R.string.app_repository_lokalnyy_profil_ne_nayden) }
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
        require(current.revision == old.revision) { AppText.get(R.string.planning_store_plan_izmenilsya_zakroyte_formu_i) }
        if (scope == PlanScope.EVENT) require(e.plan.completedTrainingId == null) { AppText.get(R.string.planning_store_izmenyayte_svyazannyy_rezultat_a_ne) }
        require(input.repeatWeekly == (scope == PlanScope.SERIES)) { AppText.get(R.string.planning_store_razovuyu_trenirovku_nelzya_prevratit_v) }
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
        require(snapshot(old.key, e, scope).revision == old.revision) { AppText.get(R.string.planning_store_plan_izmenilsya_otkroyte_podtverzhdenie_zanovo) }
        if (scope == PlanScope.SERIES) dao.updateRule(requireNotNull(e.rule).copy(isCanceled = true))
        else {
            require(e.plan.completedTrainingId == null) { AppText.get(R.string.planning_store_zavershyonnyy_plan_otmenit_nelzya) }
            persist(e.plan.copy(status = PlannedTrainingStatus.CANCELED))
        }
    }

    suspend fun complete(old: PlanSnapshot, input: AddCompletedTrainingInput, requestId: String,
        save: suspend () -> Long): Long {
        val e = event(old.key)
        e.plan.completedTrainingId?.let { id ->
            val result = requireNotNull(db.trainingDao().getTrainingBundle(id))
            require(result.training.publicId == requestId) { AppText.get(R.string.planning_store_dlya_etogo_plana_uzhe_zapisan) }
            return save() // also verifies identical payload of a retried request
        }
        require(snapshot(old.key, e, PlanScope.EVENT).revision == old.revision) { AppText.get(R.string.planning_store_plan_izmenilsya_otkroyte_ego_zanovo) }
        require(!old.canceled && !snapshot(old.key, e, PlanScope.EVENT).canceled) { AppText.get(R.string.planning_store_otmenyonnuyu_trenirovku_zavershit_nelzya) }
        require(input.sportId == e.plan.sportId) { AppText.get(R.string.planning_store_vid_sporta_dolzhen_sootvetstvovat_planu) }
        val id = save()
        require(dao.getPlanForResult(id) == null) { AppText.get(R.string.planning_store_rezultat_uzhe_svyazan_s_drugim) }
        persist(e.plan.copy(status = PlannedTrainingStatus.COMPLETED, completedTrainingId = id))
        return id
    }
}
