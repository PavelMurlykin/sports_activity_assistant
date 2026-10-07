package com.pamurlykin.sportsactivityassistant.data.sport

import com.pamurlykin.sportsactivityassistant.data.backup.ClimbingRouteBackup
import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.dao.TrainingDao
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingRouteEntity
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingDifficultyCatalog
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingRouteInput
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType
import com.pamurlykin.sportsactivityassistant.data.model.MetricUiModel
import java.time.LocalDate

object ClimbingModule : SportModule {
    override val slug = "climbing"
    override val title = "Скалолазание"

    override fun validate(input: AddCompletedTrainingInput) {
        require(input.football == null) { "Тренировка скалолазания не может содержать футбольную статистику" }
        require(input.climbingRoutes.isNotEmpty()) { "Добавьте хотя бы одну трассу" }
        input.climbingRoutes.forEach {
            require(ClimbingDifficultyCatalog.isValid(it.routeDifficulty)) { "Неизвестная категория сложности: ${it.routeDifficulty}" }
            require(it.repeatCount > 0) { "Количество попыток должно быть положительным" }
        }
    }

    override suspend fun insertDetails(dao: TrainingDao, trainingId: Long, input: AddCompletedTrainingInput) {
        dao.insertClimbingTraining(ClimbingTrainingEntity(trainingId))
        dao.insertClimbingRoutes(input.climbingRoutes.map {
            ClimbingRouteEntity(climbingTrainingId = trainingId, workoutType = it.workoutType,
                routeDifficulty = ClimbingDifficultyCatalog.normalize(it.routeDifficulty),
                isCompleted = it.isCompleted, repeatCount = it.repeatCount)
        })
    }

    override fun decodeDetails(backup: TrainingBackup, sportId: Int, complexId: Long): AddCompletedTrainingInput {
        require(backup.football == null) { "Запись скалолазания содержит футбольную статистику" }
        return AddCompletedTrainingInput(sportId, complexId, LocalDate.parse(backup.date), climbingRoutes = backup.climbingRoutes.map {
            val type = requireNotNull(ClimbingWorkoutType.entries.firstOrNull { type -> type.storageValue == it.workoutType }) {
                "Неизвестная дисциплина скалолазания: ${it.workoutType}"
            }
            ClimbingRouteInput(type, it.routeDifficulty, it.completed, it.repeatCount)
        })
    }

    override fun encodeDetails(bundle: TrainingBundle, common: TrainingBackup): TrainingBackup = common.copy(
        climbingRoutes = bundle.climbing?.routes.orEmpty().map {
            ClimbingRouteBackup(it.workoutType.storageValue, it.routeDifficulty, it.isCompleted, it.repeatCount)
        },
    )

    override fun highlights(items: List<TrainingBundle>): List<String> {
        val routes = items.flatMap { it.climbing?.routes.orEmpty() }
        return listOf("${routes.filter { it.isCompleted }.sumOf { it.repeatCount }} из ${routes.sumOf { it.repeatCount }} трасс пройдено")
    }

    override fun metrics(items: List<TrainingBundle>): List<MetricUiModel> {
        val routes = items.flatMap { it.climbing?.routes.orEmpty() }
        val attempts = routes.sumOf { it.repeatCount }
        val completedRoutes = routes.filter { it.isCompleted }
        val completed = completedRoutes.sumOf { it.repeatCount }
        val percent = if (attempts == 0) 0 else completed * 100 / attempts
        val hardest = ClimbingDifficultyCatalog.hardest(completedRoutes.map { it.routeDifficulty })
        return buildList {
            add(MetricUiModel("Тренировки", items.size.toString()))
            add(MetricUiModel("Трассы", attempts.toString()))
            add(MetricUiModel("Успешно пройдено", "$completed ($percent%)"))
            hardest?.let { add(MetricUiModel("Максимальная сложность", it)) }
            ClimbingWorkoutType.entries.forEach { type ->
                val count = routes.filter { it.workoutType == type }.sumOf { it.repeatCount }
                if (count > 0) add(MetricUiModel(type.title, count.toString()))
            }
        }
    }

    override fun details(bundle: TrainingBundle): List<String> = bundle.climbing?.routes.orEmpty().map {
        val count = if (it.repeatCount > 1) " × ${it.repeatCount}" else ""
        "${it.workoutType.title} · ${it.routeDifficulty}$count · ${if (it.isCompleted) "пройдена" else "не пройдена"}"
    }
}
