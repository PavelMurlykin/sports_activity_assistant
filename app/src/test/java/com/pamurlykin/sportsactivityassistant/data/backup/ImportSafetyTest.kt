package com.pamurlykin.sportsactivityassistant.data.backup

import com.pamurlykin.sportsactivityassistant.data.model.TrainingValidation
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ImportSafetyTest {
    private val header = "user_id;training_date;sports_complex_id;team_goals_scored;team_goals_conceded;user_goals_scored;user_assists"
    private val row = "1;2020-01-02;2;3;1;1;1"
    private fun id(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()
    private fun document() = BackupDocument(exportedAt = "2026-10-07T00:00:00Z", sports = listOf(SportBackup("football", "Футбол")),
        centers = listOf(CenterBackup(name = "Центр", sportSlugs = listOf("football"), publicId = id("center"), createdAt = "2020-01-01T00:00:00Z")),
        profiles = listOf(ProfileBackup(id("profile"), createdAt = "2020-01-01T00:00:00Z")), primaryProfilePublicId = id("profile"),
        trainings = listOf(TrainingBackup(date = "2020-01-02", sportSlug = "football", centerName = "Центр", football = FootballBackup(3, 1, 1, 1),
            publicId = id("training"), centerPublicId = id("center"), profilePublicId = id("profile"), createdAt = "2020-01-02T10:00:00.123Z")))
    private fun parse(d: BackupDocument) = ImportParser.parse(BackupCodec.encode(d).toByteArray())

    @Test fun byteIdentityKeepsTwoIdenticalCsvRowsButRepeatIsStable() {
        val bytes = "$header\n$row\n$row".toByteArray()
        val first = ImportParser.parse(bytes)
        assertEquals(2, first.document.trainings.map { it.publicId }.distinct().size)
        assertEquals(first.document, ImportParser.parse(bytes).document)
        assertNotEquals(first.document.trainings.first().publicId, ImportParser.parse(bytes + '\n'.code.toByte()).document.trainings.first().publicId)
    }

    @Test fun csvQuotesOptionalFieldsBomAndPhysicalLines() {
        val bytes = "\uFEFF\n$header;distance_km;players_per_team;duration_minutes;note\r\n$row;\"7,35\";5;60;\"one;two\r\n\"\"quoted\"\"\"\r\n$row;;;;ignored".toByteArray()
        val parsed = ImportParser.parse(bytes)
        assertEquals("Строка 3", parsed.locations[parsed.document.trainings.first().publicId])
        assertEquals("Строка 5", parsed.locations[parsed.document.trainings.last().publicId])
        val details = parsed.document.trainings.first().football!!
        assertEquals("7.35", details.distanceKm); assertEquals(5, details.playersPerTeam); assertEquals(60, details.durationMinutes)
        assertNull(parsed.document.trainings.last().football!!.distanceKm)
    }

    @Test fun malformedCsvDoesNotGuessMissingValues() {
        listOf("$header;user_id\n$row;1", "$header\n$row;extra", "$header\n1;bad;2;3;1;1;1", "$header\n\"$row", "$header\n1;2020-01-02;2;3;1;1;1x").forEach {
            assertTrue(runCatching { ImportParser.parse(it.toByteArray()) }.isFailure)
        }
    }

    @Test fun jsonIsStrictUtf8ButCsvAccepts1251() {
        val csv = "$header;note\n$row;Москва"
        assertEquals(1, ImportParser.parse(csv.toByteArray(charset("windows-1251"))).document.trainings.size)
        assertTrue(runCatching { ImportParser.parse(BackupCodec.encode(document()).toByteArray(charset("windows-1251"))) }.isFailure)
        assertTrue(runCatching { ImportParser.parse(csv.toByteArray(Charsets.UTF_16)) }.isFailure)
    }

    @Test fun streamReadIsBoundedBeforeAllocatingOversizedResult() {
        val endless = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int { b.fill(0, off, off + len); return len }
        }
        assertTrue(runCatching { ImportFiles.read(endless) }.exceptionOrNull()!!.message!!.contains("16"))
        assertTrue(ImportFiles.read(ByteArrayInputStream("small".toByteArray())).contentEquals("small".toByteArray()))
        assertTrue(runCatching { ImportParser.parse(ByteArray(0)) }.isFailure)
    }

    @Test fun validV4RetainsAllMetadata() {
        val d = document(); val parsed = parse(d)
        assertEquals(d, parsed.document); assertTrue(BackupValidation.errors(parsed).isEmpty())
    }

    @Test fun invalidDatesReferencesNumbersAndTypesHavePaths() {
        val d = document(); val t = d.trainings.single()
        listOf(t.copy(date = "2020-02-30"), t.copy(date = "+10000-01-01"), t.copy(centerPublicId = id("missing")),
            t.copy(football = t.football!!.copy(userGoalsScored = 4)), t.copy(football = t.football.copy(distanceKm = "1E+999999")),
            t.copy(football = t.football.copy(playersPerTeam = 0)), t.copy(publicId = "invalid"), t.copy(createdAt = "bad")).forEach {
            val errors = BackupValidation.errors(parse(d.copy(trainings = listOf(it))))
            assertTrue(errors.toString(), errors.any { message -> message.startsWith("trainings[0]") })
        }
    }

    @Test fun refusesUnknownFieldsMissingOrFutureVersionAndDuplicateIdentity() {
        val raw = BackupCodec.encode(document())
        assertTrue(runCatching { ImportParser.parse(raw.replace("\"schemaVersion\": 4", "\"schemaVersion\": 999").toByteArray()) }.isFailure)
        assertTrue(runCatching { ImportParser.parse(raw.replace("\"schemaVersion\": 4,", "").toByteArray()) }.isFailure)
        assertTrue(runCatching { ImportParser.parse(raw.replace("\"schemaVersion\"", "\"unknown\"").toByteArray()) }.isFailure)
        assertTrue(BackupValidation.errors(parse(document().copy(trainings = document().trainings + document().trainings))).isNotEmpty())
    }

    @Test fun recurrenceAndManualDatesUseTheSameBoundaries() {
        val date = LocalDate.parse("2026-10-07")
        TrainingValidation.recurrence(date, date, 1)
        assertTrue(runCatching { TrainingValidation.date(LocalDate.of(0, 1, 1)) }.isFailure)
        assertTrue(runCatching { TrainingValidation.recurrence(date, date.minusDays(1), 1) }.isFailure)
        assertTrue(runCatching { TrainingValidation.recurrence(date, null, 0) }.isFailure)
        val d = document()
        val rule = RecurrenceRuleBackup(date.toString(), date.toString(), "football", "Центр", intervalWeeks = 0, publicId = id("rule"),
            centerPublicId = id("center"), profilePublicId = id("profile"), createdAt = "2020-01-01T00:00:00Z")
        assertTrue(BackupValidation.errors(parse(d.copy(recurrenceRules = listOf(rule)))).any { it.startsWith("recurrenceRules[0]") })
    }
}
