package com.pamurlykin.sportsactivityassistant.data.sport

import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.dao.TrainingDao
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.MetricUiModel

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
    fun metrics(items: List<TrainingBundle>): List<MetricUiModel>
    fun highlights(items: List<TrainingBundle>): List<String>
    fun details(bundle: TrainingBundle): List<String>
}

object SportModules {
    val all: List<SportModule> = listOf(FootballModule, ClimbingModule)
    private val bySlug = all.associateBy { it.slug }
    fun find(slug: String): SportModule? = bySlug[slug]
    fun require(slug: String): SportModule = requireNotNull(find(slug)) {
        "Вид спорта «$slug» не поддерживается этой версией приложения; данные не изменены"
    }
}
