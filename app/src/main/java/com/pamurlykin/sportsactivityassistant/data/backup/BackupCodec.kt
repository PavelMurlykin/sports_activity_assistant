package com.pamurlykin.sportsactivityassistant.data.backup

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import com.pamurlykin.sportsactivityassistant.data.model.HistoricalClimbingGrades
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

object BackupCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(document: BackupDocument): String {
        var encoded = json.encodeToJsonElement(BackupDocument.serializer(), document).jsonObject
        if (document.schemaVersion < 6) {
            require(document.recurrenceRules.none { it.isCanceled } && document.plannedTrainings.all {
                it.occurrenceDate == null && it.completedTrainingPublicId == null && it.status != "completed"
            }) { "Исключения и связи с результатами требуют JSON 6" }
            fun strip(key: String, fields: Set<String>) = kotlinx.serialization.json.JsonArray(
                (encoded.getValue(key) as kotlinx.serialization.json.JsonArray).map {
                    kotlinx.serialization.json.JsonObject(it.jsonObject - fields)
                })
            encoded = kotlinx.serialization.json.JsonObject(encoded +
                ("recurrenceRules" to strip("recurrenceRules", setOf("isCanceled"))) +
                ("plannedTrainings" to strip("plannedTrainings", setOf("occurrenceDate", "completedTrainingPublicId"))))
        }
        if (document.schemaVersion < 5) {
            require(document.centers.none { it.isArchived }) { "Архивные центры требуют JSON 5" }
            encoded = kotlinx.serialization.json.JsonObject(encoded + ("centers" to
                kotlinx.serialization.json.JsonArray((encoded.getValue("centers") as kotlinx.serialization.json.JsonArray)
                    .map { kotlinx.serialization.json.JsonObject(it.jsonObject - "isArchived") })))
        }
        return json.encodeToString(encoded)
    }

    fun decode(raw: String): BackupDocument {
        val version = sourceVersion(raw)
        if (version < 6) {
            val root = json.parseToJsonElement(raw).jsonObject
            fun noFields(key: String, fields: Set<String>) = root[key]?.let { list ->
                (list as kotlinx.serialization.json.JsonArray).all { item -> fields.none { it in item.jsonObject } }
            } != false
            require(noFields("recurrenceRules", setOf("isCanceled")) &&
                noFields("plannedTrainings", setOf("occurrenceDate", "completedTrainingPublicId"))) {
                "Метаданные календаря доступны только в JSON 6"
            }
        }
        if (version < 5) {
            require(json.parseToJsonElement(raw).jsonObject["centers"]?.let { centers ->
                (centers as kotlinx.serialization.json.JsonArray).all { "isArchived" !in it.jsonObject }
            } != false) { "centers.isArchived доступно только в JSON 5" }
        }
        val document = json.decodeFromString<BackupDocument>(raw)
        require(document.schemaVersion in 1..BackupDocument.CURRENT_SCHEMA_VERSION) {
            "Версия резервной копии ${document.schemaVersion} не поддерживается"
        }
        if (document.schemaVersion >= 3) {
            require(document.trainings.flatMap { it.climbingRoutes }.all { it.gradingSystem != null }) {
                "В копии версии 3–6 должна быть указана шкала каждой трассы"
            }
            return document
        }
        require(document.trainings.flatMap { it.climbingRoutes }.all {
            it.gradingSystem == null && it.gradeCode == null && it.speedCourse == null && it.legacyWorkoutType == null
        }) { "Данные шкалы не соответствуют версии копии" }
        return document.copy(schemaVersion = 3, trainings = document.trainings.map { training -> training.copy(
            climbingRoutes = training.climbingRoutes.map { route ->
                val grade = HistoricalClimbingGrades.classify(route.workoutType, route.routeDifficulty)
                route.copy(gradingSystem = grade.system, gradeCode = grade.code)
            },
        ) })
    }

    fun sourceVersion(raw: String): Int {
        val value = requireNotNull(json.parseToJsonElement(raw).jsonObject["schemaVersion"]) {
            "JSON: поле schemaVersion обязательно"
        }.jsonPrimitive
        require(!value.isString) { "JSON: schemaVersion должно быть целым числом, не строкой" }
        val version = value.int
        require(version in 1..BackupDocument.CURRENT_SCHEMA_VERSION) { "Версия резервной копии $version не поддерживается" }
        return version
    }

    fun decodeText(bytes: ByteArray): String {
        val utf8 = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { utf8.decode(ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { charset("windows-1251").decode(ByteBuffer.wrap(bytes)).toString() }
            .removePrefix("\uFEFF")
    }
}
