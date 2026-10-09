package com.pamurlykin.sportsactivityassistant.data.sport

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import com.pamurlykin.sportsactivityassistant.data.backup.FootballBackup
import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.dao.TrainingDao
import com.pamurlykin.sportsactivityassistant.data.entity.FootballTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.FootballTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.MetricUiModel
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object FootballModule : SportModule {
    override val slug = "football"
    override val title = AppText.get(R.string.import_parser_futbol)

    override fun validate(input: AddCompletedTrainingInput, allowHistorical: Boolean) {
        val details = requireNotNull(input.football) { AppText.get(R.string.football_module_zapolnite_futbolnuyu_statistiku) }
        require(input.climbingRoutes.isEmpty()) { AppText.get(R.string.football_module_futbolnaya_trenirovka_ne_mozhet_soderzhat) }
        val errors = fieldErrors(details)
        require(errors.isEmpty()) { errors.values.first() }
    }

    /** Shared business rules for repository/import validation and inline form errors. */
    fun fieldErrors(details: FootballTrainingInput): Map<Int, String> = buildMap {
        listOf(details.teamGoalsScored, details.teamGoalsConceded, details.userGoalsScored, details.userAssists)
            .forEachIndexed { index, value -> if (value < 0) put(index, AppText.get(R.string.football_module_znachenie_ne_mozhet_byt_otritsatelnym)) }
        if (details.userGoalsScored > details.teamGoalsScored) put(2, AppText.get(R.string.football_module_lichnye_goly_ne_mogut_prevyshat))
        if (details.userAssists > details.teamGoalsScored) put(3, AppText.get(R.string.football_module_peredachi_ne_mogut_prevyshat_chislo))
        details.distanceKm?.let {
            // Room stores plain TEXT: equivalent exponent/plain forms must validate equally.
            val normalized = runCatching { it.stripTrailingZeros() }.getOrNull()
            if (it < BigDecimal.ZERO) put(4, AppText.get(R.string.football_module_distantsiya_ne_mozhet_byt_otritsatelnoy))
            else if (normalized == null || normalized.precision() > 16 || normalized.scale() > 6 ||
                normalized.precision().toLong() - normalized.scale() > 22)
                put(4, AppText.get(R.string.football_module_ne_bolee_16_znachaschih_tsifr))
        }
        if (details.playersPerTeam != null && details.playersPerTeam <= 0) put(5, AppText.get(R.string.football_module_chislo_igrokov_dolzhno_byt_polozhitelnym))
        if (details.durationMinutes != null && details.durationMinutes <= 0) put(6, AppText.get(R.string.football_module_vremya_igry_dolzhno_byt_polozhitelnym))
    }

    override suspend fun insertDetails(dao: TrainingDao, trainingId: Long, input: AddCompletedTrainingInput) {
        val it = requireNotNull(input.football)
        dao.insertFootballTraining(FootballTrainingEntity(
            trainingId, it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored,
            it.userAssists, it.distanceKm, it.playersPerTeam, it.durationMinutes,
        ))
    }

    override suspend fun updateDetails(dao: TrainingDao, bundle: TrainingBundle, input: AddCompletedTrainingInput) =
        insertDetails(dao, bundle.training.id, input)

    override fun decodeDetails(backup: TrainingBackup, sportId: Int, complexId: Long): AddCompletedTrainingInput {
        require(backup.climbingRoutes.isEmpty()) { AppText.get(R.string.football_module_futbolnaya_zapis_soderzhit_trassy) }
        val it = requireNotNull(backup.football) { AppText.get(R.string.football_module_v_futbolnoy_zapisi_otsutstvuet_statistika) }
        return AddCompletedTrainingInput(sportId, complexId, LocalDate.parse(backup.date), football = FootballTrainingInput(
            it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored, it.userAssists,
            it.distanceKm?.also { value -> require(value.length <= 64) { AppText.get(R.string.football_module_slishkom_dlinnaya_distantsiya) } }?.toBigDecimal(), it.playersPerTeam, it.durationMinutes,
        ))
    }

    override fun encodeDetails(bundle: TrainingBundle, common: TrainingBackup): TrainingBackup = common.copy(
        football = bundle.football?.let {
            FootballBackup(it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored, it.userAssists,
                it.distanceKm?.toString(), it.playersPerTeam, it.durationMinutes)
        },
    )

    override suspend fun aggregate(dao: TrainingDao, selection: StatisticsSelection, includeMetrics: Boolean): SportAggregate {
        val (user, sport, filter) = selection
        val totals = dao.footballTotals(user, sport, filter.firstDate, filter.lastDate, filter.centerId)
        if (!includeMetrics) return SportAggregate(emptyList(), listOf(AppText.get(R.string.football_module_golov, totals.personalGoals), AppText.get(R.string.football_module_peredach, totals.assists)))
        var sum = BigDecimal.ZERO
        var afterId = 0L
        while (totals.distanceCount > 0) {
            currentCoroutineContext().ensureActive()
            val batch = dao.distanceSamples(user, sport, filter.firstDate, filter.lastDate, filter.centerId, afterId)
            if (batch.isEmpty()) break
            batch.forEach { sum = sum.add(it.distanceKm) }
            afterId = batch.last().id
        }
        return result(totals, sum)
    }

    private fun fromItems(items: List<TrainingBundle>): SportAggregate {
        val rows = items.mapNotNull { it.football }
        val distances = rows.mapNotNull { it.distanceKm }
        val durations = rows.mapNotNull { it.durationMinutes }
        val players = rows.mapNotNull { it.playersPerTeam }
        return result(FootballTotals(
            games = rows.size.toLong(),
            wins = rows.count { it.teamGoalsScored > it.teamGoalsConceded }.toLong(),
            draws = rows.count { it.teamGoalsScored == it.teamGoalsConceded }.toLong(),
            losses = rows.count { it.teamGoalsScored < it.teamGoalsConceded }.toLong(),
            teamScored = rows.sumOf { it.teamGoalsScored.toLong() },
            teamConceded = rows.sumOf { it.teamGoalsConceded.toLong() },
            personalGoals = rows.sumOf { it.userGoalsScored.toLong() },
            assists = rows.sumOf { it.userAssists.toLong() },
            distanceCount = distances.size.toLong(),
            minutes = durations.sumOf { it.toLong() }, durationCount = durations.size.toLong(),
            players = players.sumOf { it.toLong() }, playersCount = players.size.toLong(),
        ), distances.fold(BigDecimal.ZERO, BigDecimal::add))
    }

    override fun highlights(items: List<TrainingBundle>) = fromItems(items).highlights
    override fun metrics(items: List<TrainingBundle>) = fromItems(items).metrics

    private fun result(t: FootballTotals, distance: BigDecimal): SportAggregate {
        fun decimal(value: BigDecimal) = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        fun average(value: BigDecimal, count: Long, unit: String) =
            if (count == 0L) AppText.get(R.string.football_module_net_dannyh) else "${decimal(value.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP))} ${unit}"
        return SportAggregate(listOf(
            MetricUiModel(AppText.get(R.string.football_module_igry), t.games.toString()),
            MetricUiModel(AppText.get(R.string.football_module_pobedy_nichi_porazheniya), "${t.wins} / ${t.draws} / ${t.losses}"),
            MetricUiModel(AppText.get(R.string.football_module_schyot_komand), "${t.teamScored}:${t.teamConceded}"),
            MetricUiModel(AppText.get(R.string.football_module_lichnye_goly), t.personalGoals.toString()),
            MetricUiModel(AppText.get(R.string.football_module_golevye_peredachi), t.assists.toString()),
            MetricUiModel(AppText.get(R.string.football_module_uchtyonnaya_distantsiya), if (t.distanceCount == 0L) AppText.get(R.string.football_module_net_dannyh) else AppText.get(R.string.football_module_km, decimal(distance))),
            MetricUiModel(AppText.get(R.string.football_module_srednyaya_distantsiya), average(distance, t.distanceCount, AppText.get(R.string.football_module_km_2))),
            MetricUiModel(AppText.get(R.string.football_module_igr_s_distantsiey), AppText.get(R.string.football_module_iz, t.distanceCount, t.games)),
            MetricUiModel(AppText.get(R.string.football_module_uchtyonnoe_vremya), if (t.durationCount == 0L) AppText.get(R.string.football_module_net_dannyh) else AppText.get(R.string.football_module_min, t.minutes)),
            MetricUiModel(AppText.get(R.string.football_module_srednyaya_dlitelnost), average(BigDecimal.valueOf(t.minutes), t.durationCount, AppText.get(R.string.football_module_min_2))),
            MetricUiModel(AppText.get(R.string.football_module_igr_so_vremenem), AppText.get(R.string.football_module_iz, t.durationCount, t.games)),
            MetricUiModel(AppText.get(R.string.football_module_sredniy_sostav_komandy), average(BigDecimal.valueOf(t.players), t.playersCount, AppText.get(R.string.football_module_igrokov))),
            MetricUiModel(AppText.get(R.string.football_module_igr_s_chislom_igrokov), AppText.get(R.string.football_module_iz, t.playersCount, t.games)),
        ), listOf(AppText.get(R.string.football_module_golov, t.personalGoals), AppText.get(R.string.football_module_peredach, t.assists)))
    }

    override fun details(bundle: TrainingBundle): List<String> = buildList {
        bundle.football?.let {
            add(AppText.get(R.string.football_module_schyot, it.teamGoalsScored, it.teamGoalsConceded))
            add(AppText.get(R.string.football_module_lichnye_goly_2, it.userGoalsScored))
            add(AppText.get(R.string.football_module_golevye_peredachi_2, it.userAssists))
            it.distanceKm?.let { value -> add(AppText.get(R.string.football_module_distantsiya_km, value.stripTrailingZeros().toPlainString())) }
            it.playersPerTeam?.let { value -> add(AppText.get(R.string.football_module_igrokov_v_komande, value)) }
            it.durationMinutes?.let { value -> add(AppText.get(R.string.football_module_vremya_igry_min, value)) }
        }
    }
}
