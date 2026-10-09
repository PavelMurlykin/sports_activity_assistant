package com.pamurlykin.sportsactivityassistant.data.backup

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.SerializationException

object ImportParser {
    fun parse(bytes: ByteArray): ParsedImport {
        val (raw, json) = ImportFiles.text(bytes)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun id(key: String) = UUID.nameUUIDFromBytes("sports-file:${hash}:${key}".toByteArray(Charsets.UTF_8)).toString()
        if (!json) {
            val rows = FootballCsvParser.parse(raw)
            require(rows.isNotEmpty()) { AppText.get(R.string.import_parser_csv_ne_soderzhit_trenirovok) }
            require(rows.size <= ImportFiles.MAX_ITEMS) { AppText.get(R.string.football_csv_parser_csv_slishkom_mnogo_zapisey) }
            val profiles = rows.map { it.sourceUserId }.distinct().map { ProfileBackup(id("profile:${it}")) }
            val centers = rows.map { it.sportsComplexId }.distinct().map {
                CenterBackup(it, AppText.get(R.string.import_parser_istoricheskiy_tsentr, it), sportSlugs = listOf("football"), publicId = id("center:${it}"))
            }
            val trainings = rows.mapIndexed { index, row -> TrainingBackup(date = row.trainingDate.toString(),
                sportSlug = "football", centerLegacyId = row.sportsComplexId, centerName = AppText.get(R.string.import_parser_istoricheskiy_tsentr, row.sportsComplexId),
                football = FootballBackup(row.teamGoalsScored, row.teamGoalsConceded, row.userGoalsScored, row.userAssists,
                    row.distanceKm?.toString(), row.playersPerTeam, row.durationMinutes), publicId = id("training:${index}"),
                centerPublicId = id("center:${row.sportsComplexId}"), profilePublicId = id("profile:${row.sourceUserId}")) }
            return ParsedImport(BackupDocument(exportedAt = "1970-01-01T00:00:00Z", sports = listOf(SportBackup("football", AppText.get(R.string.import_parser_futbol))),
                centers = centers, trainings = trainings, profiles = profiles, primaryProfilePublicId = profiles.first().publicId),
                AppText.get(R.string.import_parser_futbolnyy_csv), 0, trainings.mapIndexed { index, item -> item.publicId!! to AppText.get(R.string.import_parser_stroka, rows[index].sourceLine) }.toMap(),
                rows.map { id("profile:${it.sourceUserId}") to AppText.get(R.string.import_parser_user_id_tolko_pole_fayla, it.sourceUserId) }.toMap(), csv = true)
        }
        val (version, decoded) = try {
            BackupCodec.sourceVersion(raw) to BackupCodec.decode(raw)
        } catch (error: SerializationException) {
            // Decoder diagnostics can contain personal file contents. Keep them out of the UI.
            throw IllegalArgumentException(AppText.get(R.string.import_invalid_json), error)
        }
        if (version >= 4) return ParsedImport(decoded, "JSON ${version}", version,
            decoded.trainings.mapIndexedNotNull { index, item -> item.publicId?.let { it to "trainings[${index}]" } }.toMap(),
            decoded.profiles.map { it.publicId to (it.displayName ?: AppText.get(R.string.import_parser_lokalnyy_profil, it.publicId.take(8))) }.toMap())
        require(decoded.profiles.isEmpty() && decoded.primaryProfilePublicId == null && decoded.aliases.isEmpty() && decoded.favorites.isEmpty()) {
            AppText.get(R.string.import_parser_metadannye_profiley_sopostavleniy_ne_sootvetstvuyut_versii)
        }
        val centers = decoded.centers.mapIndexed { index, item ->
            require(item.publicId == null && item.createdAt == null) { AppText.get(R.string.import_parser_centers_uuid_ne_sootvetstvuet_staroy, index) }
            item.copy(publicId = id("center:${index}"))
        }
        val centersByNumber = centers.groupBy { it.legacyId }
        val centersByName = centers.groupBy { it.name to it.city }
        fun center(legacyId: Long?, name: String, city: String?): String {
            val matches = if (legacyId != null) centersByNumber[legacyId].orEmpty()
                else centersByName[name to city].orEmpty()
            require(matches.size == 1) { AppText.get(R.string.import_parser_tsentr_ssylka_otsutstvuet_ili, name) }
            require(matches.single().name == name && matches.single().city == city) { AppText.get(R.string.import_parser_tsentr_nomer_i_nazvanie, name) }
            return matches.single().publicId!!
        }
        val profile = ProfileBackup(id("profile"))
        val trainings = decoded.trainings.mapIndexed { index, item ->
            require(item.publicId == null && item.centerPublicId == null && item.profilePublicId == null && item.createdAt == null) {
                AppText.get(R.string.import_parser_trainings_uuid_ne_sootvetstvuet_staroy, index)
            }
            item.copy(publicId = id("training:${index}"), centerPublicId = center(item.centerLegacyId, item.centerName, item.centerCity),
                profilePublicId = profile.publicId, climbingRoutes = item.climbingRoutes.mapIndexed { routeIndex, route ->
                    require(route.publicId == null) { AppText.get(R.string.import_parser_trainings_climbingroutes_uuid_v_staroy_versii, index, routeIndex) }
                    route.copy(publicId = id("route:${index}:${routeIndex}"))
                })
        }
        val rules = decoded.recurrenceRules.mapIndexed { index, rule ->
            require(rule.publicId == null && rule.profilePublicId == null && rule.centerPublicId == null && rule.createdAt == null) { AppText.get(R.string.import_parser_recurrencerules_uuid_v_staroy_versii, index) }
            rule.copy(publicId = id("rule:${index}"), profilePublicId = profile.publicId, centerPublicId = center(null, rule.centerName, rule.centerCity))
        }
        val plans = decoded.plannedTrainings.mapIndexed { index, plan ->
            require(plan.publicId == null && plan.profilePublicId == null && plan.centerPublicId == null && plan.createdAt == null && plan.recurrenceRulePublicId == null) { AppText.get(R.string.import_parser_plannedtrainings_metadannye_v_staroy_versii, index) }
            plan.copy(publicId = id("plan:${index}"), profilePublicId = profile.publicId, centerPublicId = center(null, plan.centerName, plan.centerCity))
        }
        return ParsedImport(decoded.copy(schemaVersion = BackupDocument.CURRENT_SCHEMA_VERSION, centers = centers, trainings = trainings, recurrenceRules = rules,
            plannedTrainings = plans, profiles = listOf(profile), primaryProfilePublicId = profile.publicId), AppText.get(R.string.import_parser_json_adapter, version), version,
            trainings.mapIndexed { index, item -> item.publicId!! to "trainings[${index}]" }.toMap(), mapOf(profile.publicId to AppText.get(R.string.import_parser_istoriya_bez_svedeniy_o_profile)))
    }
}
