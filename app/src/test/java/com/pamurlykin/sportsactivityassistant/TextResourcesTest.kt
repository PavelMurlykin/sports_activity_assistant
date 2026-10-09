package com.pamurlykin.sportsactivityassistant

import com.pamurlykin.sportsactivityassistant.text.AppText
import org.junit.Assert.assertEquals
import org.junit.Test

class TextResourcesTest : ResourceTextTest() {
    @Test fun formattedLabelsUseCanonicalXmlAndAcceptNumbersAndNullValues() {
        assertEquals("2 / 7", AppText.get(R.string.paging_index, 2, 7))
        assertEquals("null · 6a", AppText.get(R.string.details_pair, null, "6a"))
        assertEquals("UUID sample", AppText.get(R.string.record_uuid, "sample"))
    }

    @Test fun simpleAndClassificationLabelsUseTheSameResources() {
        assertEquals("Сохранить", AppText.get(R.string.add_completed_training_dialog_sohranit))
        assertEquals("Fontainebleau", AppText.get(R.string.grading_fontainebleau))
    }
}
