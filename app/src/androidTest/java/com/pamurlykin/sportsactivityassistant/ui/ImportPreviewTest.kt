package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.pamurlykin.sportsactivityassistant.data.backup.*
import com.pamurlykin.sportsactivityassistant.ui.components.ImportPreviewContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ImportPreviewTest {
    @get:Rule val compose = createComposeRule()
    private fun preview() = ImportPreview("JSON 4", 2, 0, 0, 0, ImportChoices())

    @Test fun invalidPreviewBlocksApplyAndCancelHasNoApplySideEffect() {
        var applied = false; var canceled = false
        compose.setContent { MaterialTheme { ImportPreviewContent(preview().copy(errors = listOf("trainings[0]: неверная дата")), false, null, {}, { applied = true }, { canceled = true }) } }
        compose.onNodeWithText("Применить импорт").assertIsNotEnabled()
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertTrue(canceled); assertFalse(applied) }
    }

    @Test fun conflictsRequireAnExplicitKeepLocalChoice() {
        var choices: ImportChoices? = null
        compose.setContent { MaterialTheme { ImportPreviewContent(preview().copy(records = listOf(
            ImportRecordPreview("record-id", "2020-01-01 · Центр", "training", true, true, false))), false, null, { choices = it }, {}, {}) } }
        compose.onNodeWithText("Сохранить локальную запись").performScrollTo()
        compose.onNode(isToggleable()).assertIsOff().performClick()
        compose.runOnIdle { assertEquals(setOf("record-id"), choices!!.keepLocalIds) }
    }

    @Test fun possibleMatchesStayIncludedAndProfileChoiceIsExplicit() {
        var choices: ImportChoices? = null
        val p = preview().copy(profiles = listOf(ImportProfileOption("p1", "user_id=10"), ImportProfileOption("p2", "user_id=20")),
            records = listOf(ImportRecordPreview("r1", "2020-01-01 · Центр", "training", false, false, true)))
        compose.setContent { MaterialTheme { ImportPreviewContent(p, false, null, { choices = it }, {}, {}) } }
        compose.onNodeWithText("Требуется выбор").performClick()
        compose.onNodeWithText("Только user_id=20 → текущий профиль").performClick()
        compose.runOnIdle { assertEquals("p2", choices!!.selectedProfile); assertFalse(choices.preserveAllProfiles) }
        compose.onNodeWithText("Пропустить запись из файла").performScrollTo()
        compose.onNode(isToggleable()).assertIsOff().performClick()
        compose.runOnIdle { assertEquals(setOf("r1"), choices!!.skipTrainingIds) }
    }
}
