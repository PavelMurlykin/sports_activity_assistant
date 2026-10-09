package com.pamurlykin.sportsactivityassistant.data.sport

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.dao.TrainingDao
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.MetricUiModel
import com.pamurlykin.sportsactivityassistant.data.model.StatisticsSelection
import com.pamurlykin.sportsactivityassistant.data.model.SportAggregate

/** Sport-specific policy. A matching editor is registered in ui.components.SportEditors. */
interface SportModule {
    val slug: String
    val title: String
    fun validate(input: AddCompletedTrainingInput, allowHistorical: Boolean = false)
    suspend fun insertDetails(dao: TrainingDao, trainingId: Long, input: AddCompletedTrainingInput)
    fun validateEdit(input: AddCompletedTrainingInput, original: AddCompletedTrainingInput) = validate(input)
    suspend fun updateDetails(dao: TrainingDao, bundle: TrainingBundle, input: AddCompletedTrainingInput)
    fun decodeDetails(backup: TrainingBackup, sportId: Int, complexId: Long): AddCompletedTrainingInput
    fun encodeDetails(bundle: TrainingBundle, common: TrainingBackup): TrainingBackup
    /** Caller holds a read transaction; selection is identical for every aggregate. */
    suspend fun aggregate(dao: TrainingDao, selection: StatisticsSelection, includeMetrics: Boolean = true): SportAggregate
    fun metrics(items: List<TrainingBundle>): List<MetricUiModel>
    fun highlights(items: List<TrainingBundle>): List<String>
    fun details(bundle: TrainingBundle): List<String>
}

/** Immutable registry also usable by extension-contract tests without changing the shipped sports. */
class SportModuleRegistry(modules: List<SportModule>) {
    val all: List<SportModule> = modules.toList()
    init {
        require(all.all { it.slug.isNotBlank() }) { "Sport module slug must not be blank" }
        require(all.map { it.slug }.distinct().size == all.size) { "Duplicate sport module slug" }
    }
    private val bySlug = all.associateBy { it.slug }
    fun find(slug: String): SportModule? = bySlug[slug]
    fun require(slug: String): SportModule = requireNotNull(find(slug)) {
        AppText.get(R.string.sport_module_vid_sporta_ne_podderzhivaetsya, slug)
    }
}

object SportModules {
    private val registry = SportModuleRegistry(listOf(FootballModule, ClimbingModule))
    val all: List<SportModule> get() = registry.all
    fun find(slug: String): SportModule? = registry.find(slug)
    fun require(slug: String): SportModule = registry.require(slug)
}
