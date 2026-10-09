package com.pamurlykin.sportsactivityassistant.data.backup

import com.pamurlykin.sportsactivityassistant.data.model.TrainingValidation
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules
import java.time.Instant
import java.util.UUID

object BackupValidation {
    fun errors(source: ParsedImport): List<String> {
        val d = source.document
        val errors = mutableListOf<String>()
        fun check(path: String, test: () -> Unit) {
            try { test() } catch (e: RuntimeException) { errors += "$path: ${e.message}" }
        }
        fun uuid(value: String?) { require(value != null && UUID.fromString(value).toString() == value) { "Требуется канонический UUID" } }
        fun timestamp(value: String?) {
            if (source.sourceVersion >= 4) require(value != null) { "createdAt обязательно" }
            value?.let {
                val instant = Instant.parse(it)
                instant.toEpochMilli()
                require(instant.nano % 1_000_000 == 0) { "createdAt: поддерживается точность до миллисекунд" }
            }
        }
        fun text(value: String, limit: Int = 200) { require(value.isNotBlank() && value.length <= limit) { "Пустая или слишком длинная строка" } }
        fun <T> distinct(values: List<T>) { require(values.size == values.distinct().size) { "Повторяющиеся идентификаторы/ссылки" } }
        check("document") {
            require(d.objectCount() <= ImportFiles.MAX_ITEMS) { "Лимит — 100000 объектов в файле" }
            require(d.profiles.isNotEmpty()) { "profiles не может быть пустым" }
            require(d.primaryProfilePublicId in d.profiles.map { it.publicId }) { "primaryProfilePublicId отсутствует в profiles" }
            Instant.parse(d.exportedAt).toEpochMilli()
            distinct(d.sports.map { it.slug })
            val ids = d.profiles.map { it.publicId } + d.centers.map { it.publicId } + d.trainings.map { it.publicId } +
                d.trainings.flatMap { it.climbingRoutes }.map { it.publicId } + d.plannedTrainings.map { it.publicId } + d.recurrenceRules.map { it.publicId }
            ids.forEach(::uuid); distinct(ids)
            distinct(d.aliases.map { it.kind to it.sourceKey })
            distinct(d.favorites.map { it.profilePublicId to it.centerPublicId })
        }
        val sports = d.sports.map { it.slug }.toSet()
        d.sports.forEachIndexed { i, sport -> check("sports[$i]") { SportModules.require(sport.slug); text(sport.title) } }
        val centers = d.centers.associateBy { it.publicId }
        val profiles = d.profiles.map { it.publicId }.toSet()
        d.profiles.forEachIndexed { i, p -> check("profiles[$i]") { uuid(p.publicId); timestamp(p.createdAt); require(p.displayName == null || p.displayName.length <= 200) { "displayName слишком длинное" } } }
        d.centers.forEachIndexed { i, center -> check("centers[$i]") {
            uuid(center.publicId); timestamp(center.createdAt); text(center.name); require(center.city == null || center.city.length <= 200) { "city слишком длинное" }
            require(center.sportSlugs.all { it in sports }) { "sportSlugs: отсутствующая ссылка на спорт" }; distinct(center.sportSlugs)
        } }
        fun refs(profile: String?, center: String?, sport: String) {
            require(profile in profiles) { "profilePublicId: профиль не найден" }
            require(center in centers) { "centerPublicId: центр не найден" }
            require(sport in sports) { "sportSlug: вид спорта не найден" }
        }
        d.trainings.forEachIndexed { i, training ->
            val location = source.locations[training.publicId] ?: "trainings[$i]"
            check(location) {
                uuid(training.publicId); timestamp(training.createdAt); refs(training.profilePublicId, training.centerPublicId, training.sportSlug)
                TrainingValidation.parseDate(training.date)
                val module = SportModules.require(training.sportSlug)
                module.validate(module.decodeDetails(training, 1, 1), allowHistorical = true)
            }
            training.climbingRoutes.forEachIndexed { j, route -> check("$location.climbingRoutes[$j]") {
                require(route.routeDifficulty.length <= 200 && (route.legacyWorkoutType?.length ?: 0) <= 200) { "Слишком длинное исходное обозначение" }
                uuid(route.publicId)
            } }
        }
        val rules = d.recurrenceRules.associateBy { it.publicId }
        d.recurrenceRules.forEachIndexed { i, rule -> check("recurrenceRules[$i]") {
            uuid(rule.publicId); timestamp(rule.createdAt); refs(rule.profilePublicId, rule.centerPublicId, rule.sportSlug)
            require(rule.frequency in setOf("weekly", "none")) { "frequency: неизвестное значение" }
            TrainingValidation.recurrence(TrainingValidation.parseDate(rule.startDate), rule.endDate?.let(TrainingValidation::parseDate), rule.intervalWeeks)
        } }
        d.plannedTrainings.forEachIndexed { i, plan -> check("plannedTrainings[$i]") {
            uuid(plan.publicId); timestamp(plan.createdAt); refs(plan.profilePublicId, plan.centerPublicId, plan.sportSlug); TrainingValidation.parseDate(plan.date)
            require(plan.status in (if (source.sourceVersion >= 6) setOf("planned", "canceled", "completed") else setOf("planned", "canceled"))) { "status: неизвестное значение" }
            plan.recurrenceRulePublicId?.let { id ->
                val rule = requireNotNull(rules[id]) { "recurrenceRulePublicId: серия не найдена" }
                require(rule.profilePublicId == plan.profilePublicId && (source.sourceVersion >= 6 ||
                    rule.sportSlug == plan.sportSlug && rule.centerPublicId == plan.centerPublicId)) { "Ссылка на серию противоречит общим атрибутам плана" }
            }
        } }
        check("plannedTrainings.links") {
            distinct(d.plannedTrainings.mapNotNull { it.completedTrainingPublicId })
            distinct(d.plannedTrainings.filter { it.occurrenceDate != null }.map { it.recurrenceRulePublicId to it.occurrenceDate })
            val trainings = d.trainings.associateBy { it.publicId }
            d.plannedTrainings.forEach { plan ->
                require((plan.status == "completed") == (plan.completedTrainingPublicId != null)) { "Завершённый план должен иметь ссылку на результат" }
                plan.occurrenceDate?.let { TrainingValidation.parseDate(it); require(plan.recurrenceRulePublicId != null) { "Исходная дата допустима только в серии" } }
                plan.completedTrainingPublicId?.let { id ->
                    val t = requireNotNull(trainings[id]) { "Связанный результат отсутствует в файле" }
                    require(t.profilePublicId == plan.profilePublicId && t.sportSlug == plan.sportSlug) { "Связанный результат принадлежит другому профилю или спорту" }
                }
            }
        }
        d.favorites.forEachIndexed { i, item -> check("favorites[$i]") {
            require(item.profilePublicId in profiles && item.centerPublicId in centers) { "Отсутствующая ссылка" }; timestamp(item.createdAt)
        } }
        d.aliases.forEachIndexed { i, alias -> check("aliases[$i]") {
            text(alias.sourceKey, 512); uuid(alias.targetPublicId)
            require(when (alias.kind) { "center" -> alias.targetPublicId in centers; "profile" -> alias.targetPublicId in profiles; else -> false }) { "kind/targetPublicId: неизвестный тип или отсутствующая ссылка" }
            require(alias.sourceKey !in centers.keys && alias.sourceKey !in profiles || alias.sourceKey == alias.targetPublicId) { "Сопоставление противоречит UUID существующего объекта" }
        } }
        return errors.distinct()
    }
}
