package com.pamurlykin.sportsactivityassistant.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test
    fun backupRoundTripKeepsSportSpecificData() {
        val source = BackupDocument(
            exportedAt = "2026-08-07T10:00:00Z",
            sports = listOf(SportBackup("football", "Футбол")),
            centers = listOf(CenterBackup(2, "Арена", "Москва", listOf("football"))),
            trainings = listOf(
                TrainingBackup(
                    date = "2026-08-01",
                    sportSlug = "football",
                    centerName = "Арена",
                    centerCity = "Москва",
                    football = FootballBackup(4, 2, 2, 1, "7.2", 6, 60),
                ),
            ),
        )

        val json = BackupCodec.encode(source)
        val restored = BackupCodec.decode(json)

        assertTrue(json.contains("schemaVersion"))
        assertEquals(source, restored)
    }

    @Test
    fun decodesWindows1251CsvText() {
        val text = "Футбол;Москва"
        assertEquals(text, BackupCodec.decodeText(text.toByteArray(charset("windows-1251"))))
    }
}
