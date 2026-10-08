package com.pamurlykin.sportsactivityassistant.data.model

import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules
import kotlinx.serialization.json.Json

/** The revision is a canonical snapshot, not an editable field or a database version. */
data class TrainingEditSnapshot(val id: Long, val input: AddCompletedTrainingInput, val revision: String) {
    companion object {
        fun restore(id: Long, sportId: Int, centerId: Long, revision: String): TrainingEditSnapshot {
            val backup = Json.decodeFromString(TrainingBackup.serializer(), revision)
            return TrainingEditSnapshot(id, SportModules.require(backup.sportSlug).decodeDetails(backup, sportId, centerId), revision)
        }
    }
}
