package com.pamurlykin.sportsactivityassistant.data.sport

import com.pamurlykin.sportsactivityassistant.data.backup.ClimbingRouteBackup
import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.dao.TrainingDao
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingRouteEntity
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingDifficultyCatalog as Catalog
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingRouteInput
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType
import com.pamurlykin.sportsactivityassistant.data.model.HistoricalClimbingGrades
import com.pamurlykin.sportsactivityassistant.data.model.MetricUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SpeedCourse
import com.pamurlykin.sportsactivityassistant.data.model.*
import java.time.LocalDate

object ClimbingModule : SportModule {
    override val slug = "climbing"
    override val title = "Скалолазание"

    override fun validate(input: AddCompletedTrainingInput, allowHistorical: Boolean) {
        require(input.football == null) { "Тренировка скалолазания не может содержать футбольную статистику" }
        require(input.climbingRoutes.isNotEmpty()) { "Добавьте хотя бы одну трассу" }
        input.climbingRoutes.forEach { route ->
            require(route.repeatCount > 0) { "Количество попыток должно быть положительным" }
            if (route.gradingSystem == Catalog.LEGACY) {
                require(allowHistorical) { "Для новой трассы выберите подтверждённую шкалу" }
                require(route.gradeCode == null && route.speedCourse == null) { "Неоднозначная категория не может содержать подтверждённую оценку" }
                require(route.workoutType != ClimbingWorkoutType.UNKNOWN || !route.legacyWorkoutType.isNullOrBlank()) {
                    "Неизвестная дисциплина скалолазания"
                }
                require(route.workoutType == ClimbingWorkoutType.UNKNOWN || route.legacyWorkoutType == null) {
                    "Историческая дисциплина не соответствует типу трассы"
                }
            } else {
                require(route.workoutType in ClimbingWorkoutType.supported && route.legacyWorkoutType == null) { "Неизвестная дисциплина скалолазания" }
                require(route.gradingSystem == Catalog.systemFor(route.workoutType)) { "Шкала не соответствует дисциплине" }
                if (route.workoutType == ClimbingWorkoutType.SPEED) {
                    require(route.gradeCode == null && route.routeDifficulty.isEmpty()) { "Скорость не оценивается категорией французской шкалы" }
                    require(SpeedCourse.entries.any { it.code == route.speedCourse }) { "Выберите трассу скорости" }
                } else {
                    val grade = requireNotNull(route.gradeCode?.let { Catalog.find(route.gradingSystem, it) }) {
                        "Неизвестная категория сложности: ${route.routeDifficulty}"
                    }
                    require(route.gradeCode == grade.code && Catalog.find(route.gradingSystem, route.routeDifficulty)?.code == grade.code) {
                        "Код категории не соответствует обозначению"
                    }
                    require(route.speedCourse == null) { "У трудности и болдера нет типа трассы скорости" }
                }
            }
        }
    }

    override suspend fun insertDetails(dao: TrainingDao, trainingId: Long, input: AddCompletedTrainingInput) {
        dao.insertClimbingTraining(ClimbingTrainingEntity(trainingId))
        dao.insertClimbingRoutes(input.climbingRoutes.map {
            ClimbingRouteEntity(climbingTrainingId = trainingId, workoutType = it.workoutType,
                routeDifficulty = it.routeDifficulty, isCompleted = it.isCompleted, repeatCount = it.repeatCount,
                gradingSystem = it.gradingSystem, gradeCode = it.gradeCode, speedCourse = it.speedCourse,
                legacyWorkoutType = it.legacyWorkoutType, publicId = it.publicId ?: java.util.UUID.randomUUID().toString())
        })
    }

    override fun validateEdit(input: AddCompletedTrainingInput, original: AddCompletedTrainingInput) {
        validate(input, allowHistorical = true)
        val old = original.climbingRoutes.associateBy { it.publicId }
        val ids = input.climbingRoutes.mapNotNull { it.publicId }
        require(ids.distinct().size == ids.size && ids.all { runCatching { java.util.UUID.fromString(it).toString() == it }.getOrDefault(false) }) { "Идентификаторы трасс не соответствуют тренировке" }
        input.climbingRoutes.filter { it.gradingSystem == Catalog.LEGACY }.forEach { route ->
            val previous = old[route.publicId]
            require(previous != null && previous.copy(isCompleted = route.isCompleted, repeatCount = route.repeatCount) == route) {
                "Историческую категорию нельзя создать или изменить без подтверждённой шкалы"
            }
        }
    }

    override suspend fun updateDetails(dao: TrainingDao, bundle: TrainingBundle, input: AddCompletedTrainingInput) {
        val existing = bundle.climbing?.routes.orEmpty().associateBy { it.publicId }
        val retained = input.climbingRoutes.mapNotNull { it.publicId }.toSet()
        dao.deleteClimbingRoutes(existing.values.filter { it.publicId !in retained }.map { it.id })
        val items = input.climbingRoutes.map { route ->
            ClimbingRouteEntity(
                id = existing[route.publicId]?.id ?: 0L, climbingTrainingId = bundle.training.id,
                workoutType = route.workoutType, routeDifficulty = route.routeDifficulty,
                isCompleted = route.isCompleted, repeatCount = route.repeatCount,
                gradingSystem = route.gradingSystem, gradeCode = route.gradeCode,
                speedCourse = route.speedCourse, legacyWorkoutType = route.legacyWorkoutType,
                publicId = route.publicId ?: java.util.UUID.randomUUID().toString(),
            )
        }
        dao.updateClimbingRoutes(items.filter { it.id != 0L })
        dao.insertClimbingRoutes(items.filter { it.id == 0L })
    }

    override fun decodeDetails(backup: TrainingBackup, sportId: Int, complexId: Long): AddCompletedTrainingInput {
        require(backup.football == null) { "Запись скалолазания содержит футбольную статистику" }
        return AddCompletedTrainingInput(sportId, complexId, LocalDate.parse(backup.date), climbingRoutes = backup.climbingRoutes.map {
            val type = ClimbingWorkoutType.fromStorage(it.workoutType)
            require(type != ClimbingWorkoutType.UNKNOWN || (it.gradingSystem == Catalog.LEGACY && it.legacyWorkoutType == it.workoutType)) {
                "Неизвестная дисциплина скалолазания: ${it.workoutType}"
            }
            val historical = HistoricalClimbingGrades.classify(it.workoutType, it.routeDifficulty)
            ClimbingRouteInput(type, it.routeDifficulty, it.completed, it.repeatCount,
                it.gradingSystem ?: historical.system,
                if (it.gradingSystem == null) historical.code else it.gradeCode,
                it.speedCourse, it.legacyWorkoutType, it.publicId)
        })
    }

    fun encodeRoute(route: ClimbingRouteEntity): ClimbingRouteBackup = ClimbingRouteBackup(
        route.legacyWorkoutType ?: route.workoutType.storageValue, route.routeDifficulty,
        route.isCompleted, route.repeatCount, route.gradingSystem, route.gradeCode,
        route.speedCourse, route.legacyWorkoutType, route.publicId,
    )

    override fun encodeDetails(bundle: TrainingBundle, common: TrainingBackup): TrainingBackup = common.copy(
        climbingRoutes = bundle.climbing?.routes.orEmpty().map(::encodeRoute),
    )

    override suspend fun aggregate(dao: TrainingDao, selection: StatisticsSelection, includeMetrics: Boolean): SportAggregate {
        val (user, sport, filter) = selection
        val trainings = dao.trainingCount(user, sport, filter.firstDate, filter.lastDate, filter.centerId)
        return result(trainings, dao.climbingBuckets(user, sport, filter.firstDate, filter.lastDate, filter.centerId)).let {
            if (includeMetrics) it else it.copy(metrics = emptyList())
        }
    }

    private fun fromItems(items: List<TrainingBundle>): SportAggregate {
        val buckets = items.flatMap { it.climbing?.routes.orEmpty() }.map { r ->
            ClimbingBucket(r.workoutType, r.gradingSystem, r.gradeCode, r.speedCourse,
                r.gradeCode != null && Catalog.find(r.gradingSystem, r.routeDifficulty)?.code == r.gradeCode,
                1L, r.repeatCount.toLong(), if (r.isCompleted) r.repeatCount.toLong() else 0L)
        }
        return result(items.size, buckets)
    }

    override fun highlights(items: List<TrainingBundle>) = fromItems(items).highlights
    override fun metrics(items: List<TrainingBundle>) = fromItems(items).metrics

    private fun result(trainings: Int, buckets: List<ClimbingBucket>): SportAggregate {
        val attempts = buckets.sumOf { it.attempts }
        val completed = buckets.sumOf { it.completed }
        val percent = if (attempts == 0L) 0 else completed * 100 / attempts
        val metrics = buildList {
            add(MetricUiModel("Тренировки", trainings.toString()))
            add(MetricUiModel("Записи трасс", buckets.sumOf { it.rowCount }.toString()))
            add(MetricUiModel("Попытки (с повторами)", attempts.toString()))
            add(MetricUiModel("Успешно пройдено", "$completed ($percent%)"))
            ClimbingWorkoutType.entries.forEach { type ->
                val groups = buckets.filter { it.workoutType == type }
                if (groups.isNotEmpty()) add(MetricUiModel(
                    "${type.title} · успешно / попытки", "${groups.sumOf { it.completed }} / ${groups.sumOf { it.attempts }}"))
            }
            listOf(ClimbingWorkoutType.DIFFICULTY, ClimbingWorkoutType.BOULDERING).forEach { type ->
                val system = Catalog.systemFor(type)
                val confirmed = buckets.filter { it.workoutType == type && it.gradingSystem == system &&
                    it.speedCourse == null && it.gradeMatches && Catalog.find(system, it.gradeCode.orEmpty()) != null }
                val hardest = Catalog.hardest(system, confirmed.filter { it.completed > 0 }.mapNotNull { it.gradeCode })
                add(MetricUiModel("Максимум · ${type.title} (${Catalog.title(system)})", hardest?.label ?: "Нет успешных"))
                confirmed.groupBy { it.gradeCode }.entries.sortedBy { Catalog.find(system, it.key!!)?.rank }.forEach { (code, groups) ->
                    add(MetricUiModel("${type.title} · ${Catalog.find(system, code!!)?.label} · успешно / попытки",
                        "${groups.sumOf { it.completed }} / ${groups.sumOf { it.attempts }}"))
                }
            }
            SpeedCourse.entries.forEach { course ->
                val groups = buckets.filter { it.workoutType == ClimbingWorkoutType.SPEED && it.speedCourse == course.code && it.gradingSystem == Catalog.NONE }
                if (groups.isNotEmpty()) add(MetricUiModel("Скорость · ${course.title} · успешно / попытки",
                    "${groups.sumOf { it.completed }} / ${groups.sumOf { it.attempts }}"))
            }
            val legacy = buckets.filter { it.gradingSystem == Catalog.LEGACY }.sumOf { it.attempts }
            if (legacy > 0) add(MetricUiModel("Исторические категории без сравнения", legacy.toString()))
        }
        return SportAggregate(metrics, listOf("$completed из $attempts попыток успешны"))
    }

    override fun details(bundle: TrainingBundle): List<String> = bundle.climbing?.routes.orEmpty().map {
        val count = if (it.repeatCount > 1) " × ${it.repeatCount}" else ""
        val category = when {
            it.gradingSystem == Catalog.LEGACY ->
                "${it.routeDifficulty.ifEmpty { "без категории" }} (историческая: шкала/трасса не подтверждена)"
            it.workoutType == ClimbingWorkoutType.SPEED ->
                SpeedCourse.entries.firstOrNull { course -> course.code == it.speedCourse }?.title ?: "Неизвестная трасса скорости"
            else -> "${it.gradeCode?.let { code -> Catalog.find(it.gradingSystem, code)?.label } ?: it.routeDifficulty} · ${Catalog.title(it.gradingSystem)}"
        }
        "${it.legacyWorkoutType ?: it.workoutType.title} · $category$count · ${if (it.isCompleted) "пройдена" else "не пройдена"}"
    }
}
