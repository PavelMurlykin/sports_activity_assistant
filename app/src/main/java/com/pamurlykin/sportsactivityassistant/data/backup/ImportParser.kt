package com.pamurlykin.sportsactivityassistant.data.backup

import java.security.MessageDigest
import java.util.UUID

object ImportParser {
    fun parse(bytes: ByteArray): ParsedImport {
        val (raw, json) = ImportFiles.text(bytes)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun id(key: String) = UUID.nameUUIDFromBytes("sports-file:$hash:$key".toByteArray(Charsets.UTF_8)).toString()
        if (!json) {
            val rows = FootballCsvParser.parse(raw)
            require(rows.isNotEmpty()) { "CSV не содержит тренировок" }
            require(rows.size <= ImportFiles.MAX_ITEMS) { "CSV: слишком много записей" }
            val profiles = rows.map { it.sourceUserId }.distinct().map { ProfileBackup(id("profile:$it")) }
            val centers = rows.map { it.sportsComplexId }.distinct().map {
                CenterBackup(it, "Исторический центр #$it", sportSlugs = listOf("football"), publicId = id("center:$it"))
            }
            val trainings = rows.mapIndexed { index, row -> TrainingBackup(date = row.trainingDate.toString(),
                sportSlug = "football", centerLegacyId = row.sportsComplexId, centerName = "Исторический центр #${row.sportsComplexId}",
                football = FootballBackup(row.teamGoalsScored, row.teamGoalsConceded, row.userGoalsScored, row.userAssists,
                    row.distanceKm?.toString(), row.playersPerTeam, row.durationMinutes), publicId = id("training:$index"),
                centerPublicId = id("center:${row.sportsComplexId}"), profilePublicId = id("profile:${row.sourceUserId}")) }
            return ParsedImport(BackupDocument(exportedAt = "1970-01-01T00:00:00Z", sports = listOf(SportBackup("football", "Футбол")),
                centers = centers, trainings = trainings, profiles = profiles, primaryProfilePublicId = profiles.first().publicId),
                "Футбольный CSV", 0, trainings.mapIndexed { index, item -> item.publicId!! to "Строка ${rows[index].sourceLine}" }.toMap(),
                rows.map { id("profile:${it.sourceUserId}") to "user_id=${it.sourceUserId} (только поле файла)" }.toMap(), csv = true)
        }
        val version = BackupCodec.sourceVersion(raw)
        val decoded = BackupCodec.decode(raw)
        if (version >= 4) return ParsedImport(decoded, "JSON $version", version,
            decoded.trainings.mapIndexedNotNull { index, item -> item.publicId?.let { it to "trainings[$index]" } }.toMap(),
            decoded.profiles.map { it.publicId to (it.displayName ?: "Локальный профиль ${it.publicId.take(8)}") }.toMap())
        require(decoded.profiles.isEmpty() && decoded.primaryProfilePublicId == null && decoded.aliases.isEmpty() && decoded.favorites.isEmpty()) {
            "Метаданные профилей/сопоставлений не соответствуют версии JSON"
        }
        val centers = decoded.centers.mapIndexed { index, item ->
            require(item.publicId == null && item.createdAt == null) { "centers[$index]: UUID не соответствует старой версии" }
            item.copy(publicId = id("center:$index"))
        }
        val centersByNumber = centers.groupBy { it.legacyId }
        val centersByName = centers.groupBy { it.name to it.city }
        fun center(legacyId: Long?, name: String, city: String?): String {
            val matches = if (legacyId != null) centersByNumber[legacyId].orEmpty()
                else centersByName[name to city].orEmpty()
            require(matches.size == 1) { "Центр «$name»: ссылка отсутствует или неоднозначна" }
            require(matches.single().name == name && matches.single().city == city) { "Центр «$name»: номер и название противоречат друг другу" }
            return matches.single().publicId!!
        }
        val profile = ProfileBackup(id("profile"))
        val trainings = decoded.trainings.mapIndexed { index, item ->
            require(item.publicId == null && item.centerPublicId == null && item.profilePublicId == null && item.createdAt == null) {
                "trainings[$index]: UUID не соответствует старой версии"
            }
            item.copy(publicId = id("training:$index"), centerPublicId = center(item.centerLegacyId, item.centerName, item.centerCity),
                profilePublicId = profile.publicId, climbingRoutes = item.climbingRoutes.mapIndexed { routeIndex, route ->
                    require(route.publicId == null) { "trainings[$index].climbingRoutes[$routeIndex]: UUID в старой версии" }
                    route.copy(publicId = id("route:$index:$routeIndex"))
                })
        }
        val rules = decoded.recurrenceRules.mapIndexed { index, rule ->
            require(rule.publicId == null && rule.profilePublicId == null && rule.centerPublicId == null && rule.createdAt == null) { "recurrenceRules[$index]: UUID в старой версии" }
            rule.copy(publicId = id("rule:$index"), profilePublicId = profile.publicId, centerPublicId = center(null, rule.centerName, rule.centerCity))
        }
        val plans = decoded.plannedTrainings.mapIndexed { index, plan ->
            require(plan.publicId == null && plan.profilePublicId == null && plan.centerPublicId == null && plan.createdAt == null && plan.recurrenceRulePublicId == null) { "plannedTrainings[$index]: метаданные в старой версии" }
            plan.copy(publicId = id("plan:$index"), profilePublicId = profile.publicId, centerPublicId = center(null, plan.centerName, plan.centerCity))
        }
        return ParsedImport(decoded.copy(schemaVersion = BackupDocument.CURRENT_SCHEMA_VERSION, centers = centers, trainings = trainings, recurrenceRules = rules,
            plannedTrainings = plans, profiles = listOf(profile), primaryProfilePublicId = profile.publicId), "JSON $version (адаптер)", version,
            trainings.mapIndexed { index, item -> item.publicId!! to "trainings[$index]" }.toMap(), mapOf(profile.publicId to "История без сведений о профиле"))
    }
}
