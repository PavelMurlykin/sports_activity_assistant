package com.pamurlykin.sportsactivityassistant.data.backup

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import com.pamurlykin.sportsactivityassistant.data.model.TrainingValidation
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules
import java.time.Instant
import java.util.UUID

object BackupValidation {
    fun errors(source: ParsedImport): List<String> {
        val d = source.document
        val errors = mutableListOf<String>()
        fun check(path: String, test: () -> Unit) {
            try { test() } catch (e: RuntimeException) { errors += "${path}: ${e.message}" }
        }
        fun uuid(value: String?) { require(value != null && UUID.fromString(value).toString() == value) { AppText.get(R.string.backup_validation_trebuetsya_kanonicheskiy_uuid) } }
        fun timestamp(value: String?) {
            if (source.sourceVersion >= 4) require(value != null) { AppText.get(R.string.backup_validation_createdat_obyazatelno) }
            value?.let {
                val instant = Instant.parse(it)
                instant.toEpochMilli()
                require(instant.nano % 1_000_000 == 0) { AppText.get(R.string.backup_validation_createdat_podderzhivaetsya_tochnost_do_millisekund) }
            }
        }
        fun text(value: String, limit: Int = 200) { require(value.isNotBlank() && value.length <= limit) { AppText.get(R.string.backup_validation_pustaya_ili_slishkom_dlinnaya_stroka) } }
        fun <T> distinct(values: List<T>) { require(values.size == values.distinct().size) { AppText.get(R.string.backup_validation_povtoryayuschiesya_identifikatory_ssylki) } }
        check("document") {
            require(d.objectCount() <= ImportFiles.MAX_ITEMS) { AppText.get(R.string.backup_validation_limit_100000_obektov_v) }
            require(d.profiles.isNotEmpty()) { AppText.get(R.string.backup_validation_profiles_ne_mozhet_byt_pustym) }
            require(d.primaryProfilePublicId in d.profiles.map { it.publicId }) { AppText.get(R.string.backup_validation_primaryprofilepublicid_otsutstvuet_v_profiles) }
            Instant.parse(d.exportedAt).toEpochMilli()
            distinct(d.sports.map { it.slug })
            val ids = d.profiles.map { it.publicId } + d.centers.map { it.publicId } + d.trainings.map { it.publicId } +
                d.trainings.flatMap { it.climbingRoutes }.map { it.publicId } + d.plannedTrainings.map { it.publicId } + d.recurrenceRules.map { it.publicId }
            ids.forEach(::uuid); distinct(ids)
            distinct(d.aliases.map { it.kind to it.sourceKey })
            distinct(d.favorites.map { it.profilePublicId to it.centerPublicId })
        }
        val sports = d.sports.map { it.slug }.toSet()
        d.sports.forEachIndexed { i, sport -> check("sports[${i}]") { SportModules.require(sport.slug); text(sport.title) } }
        val centers = d.centers.associateBy { it.publicId }
        val profiles = d.profiles.map { it.publicId }.toSet()
        d.profiles.forEachIndexed { i, p -> check("profiles[${i}]") { uuid(p.publicId); timestamp(p.createdAt); require(p.displayName == null || p.displayName.length <= 200) { AppText.get(R.string.backup_validation_displayname_slishkom_dlinnoe) } } }
        d.centers.forEachIndexed { i, center -> check("centers[${i}]") {
            uuid(center.publicId); timestamp(center.createdAt); text(center.name); require(center.city == null || center.city.length <= 200) { AppText.get(R.string.backup_validation_city_slishkom_dlinnoe) }
            require(center.sportSlugs.all { it in sports }) { AppText.get(R.string.backup_validation_sportslugs_otsutstvuyuschaya_ssylka_na_sport) }; distinct(center.sportSlugs)
        } }
        fun refs(profile: String?, center: String?, sport: String) {
            require(profile in profiles) { AppText.get(R.string.backup_validation_profilepublicid_profil_ne_nayden) }
            require(center in centers) { AppText.get(R.string.backup_validation_centerpublicid_tsentr_ne_nayden) }
            require(sport in sports) { AppText.get(R.string.backup_validation_sportslug_vid_sporta_ne_nayden) }
        }
        d.trainings.forEachIndexed { i, training ->
            val location = source.locations[training.publicId] ?: "trainings[${i}]"
            check(location) {
                uuid(training.publicId); timestamp(training.createdAt); refs(training.profilePublicId, training.centerPublicId, training.sportSlug)
                TrainingValidation.parseDate(training.date)
                val module = SportModules.require(training.sportSlug)
                module.validate(module.decodeDetails(training, 1, 1), allowHistorical = true)
            }
            training.climbingRoutes.forEachIndexed { j, route -> check("${location}.climbingRoutes[${j}]") {
                require(route.routeDifficulty.length <= 200 && (route.legacyWorkoutType?.length ?: 0) <= 200) { AppText.get(R.string.backup_validation_slishkom_dlinnoe_ishodnoe_oboznachenie) }
                uuid(route.publicId)
            } }
        }
        val rules = d.recurrenceRules.associateBy { it.publicId }
        d.recurrenceRules.forEachIndexed { i, rule -> check("recurrenceRules[${i}]") {
            uuid(rule.publicId); timestamp(rule.createdAt); refs(rule.profilePublicId, rule.centerPublicId, rule.sportSlug)
            require(rule.frequency in setOf("weekly", "none")) { AppText.get(R.string.backup_validation_frequency_neizvestnoe_znachenie) }
            TrainingValidation.recurrence(TrainingValidation.parseDate(rule.startDate), rule.endDate?.let(TrainingValidation::parseDate), rule.intervalWeeks)
        } }
        d.plannedTrainings.forEachIndexed { i, plan -> check("plannedTrainings[${i}]") {
            uuid(plan.publicId); timestamp(plan.createdAt); refs(plan.profilePublicId, plan.centerPublicId, plan.sportSlug); TrainingValidation.parseDate(plan.date)
            require(plan.status in (if (source.sourceVersion >= 6) setOf("planned", "canceled", "completed") else setOf("planned", "canceled"))) { AppText.get(R.string.backup_validation_status_neizvestnoe_znachenie) }
            plan.recurrenceRulePublicId?.let { id ->
                val rule = requireNotNull(rules[id]) { AppText.get(R.string.backup_validation_recurrencerulepublicid_seriya_ne_naydena) }
                require(rule.profilePublicId == plan.profilePublicId && (source.sourceVersion >= 6 ||
                    rule.sportSlug == plan.sportSlug && rule.centerPublicId == plan.centerPublicId)) { AppText.get(R.string.backup_validation_ssylka_na_seriyu_protivorechit_obschim) }
            }
        } }
        check("plannedTrainings.links") {
            distinct(d.plannedTrainings.mapNotNull { it.completedTrainingPublicId })
            distinct(d.plannedTrainings.filter { it.occurrenceDate != null }.map { it.recurrenceRulePublicId to it.occurrenceDate })
            val trainings = d.trainings.associateBy { it.publicId }
            d.plannedTrainings.forEach { plan ->
                require((plan.status == "completed") == (plan.completedTrainingPublicId != null)) { AppText.get(R.string.backup_validation_zavershyonnyy_plan_dolzhen_imet_ssylku) }
                plan.occurrenceDate?.let { TrainingValidation.parseDate(it); require(plan.recurrenceRulePublicId != null) { AppText.get(R.string.backup_validation_ishodnaya_data_dopustima_tolko_v) } }
                plan.completedTrainingPublicId?.let { id ->
                    val t = requireNotNull(trainings[id]) { AppText.get(R.string.backup_validation_svyazannyy_rezultat_otsutstvuet_v_fayle) }
                    require(t.profilePublicId == plan.profilePublicId && t.sportSlug == plan.sportSlug) { AppText.get(R.string.backup_validation_svyazannyy_rezultat_prinadlezhit_drugomu_profilyu) }
                }
            }
        }
        d.favorites.forEachIndexed { i, item -> check("favorites[${i}]") {
            require(item.profilePublicId in profiles && item.centerPublicId in centers) { AppText.get(R.string.backup_validation_otsutstvuyuschaya_ssylka) }; timestamp(item.createdAt)
        } }
        d.aliases.forEachIndexed { i, alias -> check("aliases[${i}]") {
            text(alias.sourceKey, 512); uuid(alias.targetPublicId)
            require(when (alias.kind) { "center" -> alias.targetPublicId in centers; "profile" -> alias.targetPublicId in profiles; else -> false }) { AppText.get(R.string.backup_validation_kind_targetpublicid_neizvestnyy_tip_ili_otsutstvuyuschaya) }
            require(alias.sourceKey !in centers.keys && alias.sourceKey !in profiles || alias.sourceKey == alias.targetPublicId) { AppText.get(R.string.backup_validation_sopostavlenie_protivorechit_uuid_suschestvuyuschego_obekta) }
        } }
        return errors.distinct()
    }
}
