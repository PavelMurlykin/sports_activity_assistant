package com.pamurlykin.sportsactivityassistant.data.backup

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import com.pamurlykin.sportsactivityassistant.data.model.HistoricalClimbingGrades
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BackupCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(document: BackupDocument): String = json.encodeToString(document)

    fun decode(raw: String): BackupDocument {
        val document = json.decodeFromString<BackupDocument>(raw)
        require(document.schemaVersion in 1..BackupDocument.CURRENT_SCHEMA_VERSION) {
            "Версия резервной копии ${document.schemaVersion} не поддерживается"
        }
        if (document.schemaVersion >= 3) {
            require(document.trainings.flatMap { it.climbingRoutes }.all { it.gradingSystem != null }) {
                "В копии версии 3 должна быть указана шкала каждой трассы"
            }
            return document
        }
        require(document.trainings.flatMap { it.climbingRoutes }.all {
            it.gradingSystem == null && it.gradeCode == null && it.speedCourse == null && it.legacyWorkoutType == null
        }) { "Данные шкалы не соответствуют версии копии" }
        return document.copy(schemaVersion = BackupDocument.CURRENT_SCHEMA_VERSION, trainings = document.trainings.map { training -> training.copy(
            climbingRoutes = training.climbingRoutes.map { route ->
                val grade = HistoricalClimbingGrades.classify(route.workoutType, route.routeDifficulty)
                route.copy(gradingSystem = grade.system, gradeCode = grade.code)
            },
        ) })
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
