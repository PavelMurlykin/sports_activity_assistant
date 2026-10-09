package com.pamurlykin.sportsactivityassistant.ui.components

import kotlinx.coroutines.isActive

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.CancellationException

@Composable
fun SportsCenterDialog(
    center: SportsCenterUiModel?,
    sports: List<SportSummaryUiModel>,
    onDismiss: () -> Unit,
    onSave: suspend (SaveSportsCenterInput) -> Long,
    onSaved: (Long) -> Unit,
    initialSportId: Int? = null,
) {
    var name by rememberSaveable(center?.id) { mutableStateOf(center?.name.orEmpty()) }
    var city by rememberSaveable(center?.id) { mutableStateOf(center?.city.orEmpty()) }
    var selectedIds by rememberSaveable(center?.id) {
        mutableStateOf((center?.sports?.map { it.id } ?: listOfNotNull(initialSportId)).toIntArray())
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val requestId = rememberSaveable { java.util.UUID.randomUUID().toString() }
    LaunchedEffect(busy) {
        if (busy) {
            try {
                val id = onSave(SaveSportsCenterInput(center?.id, name, city, selectedIds.toSet(), requestId))
                onSaved(id)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: AppText.get(R.string.sports_center_dialog_ne_udalos_sohranit_tsentr) }
            finally { if (kotlinx.coroutines.currentCoroutineContext().isActive) busy = false }
        }
    }
    AdaptiveAlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (center == null) AppText.get(R.string.sports_center_dialog_novyy_sportivnyy_tsentr) else AppText.get(R.string.sports_center_dialog_izmenit_tsentr)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (busy) return@TextButton
                busy = true
            }) { Text(if (busy) AppText.get(R.string.add_completed_training_dialog_sohranenie) else AppText.get(R.string.add_completed_training_dialog_sohranit)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(AppText.get(R.string.add_completed_training_dialog_otmena)) } },
        text = {
            Column(Modifier.dialogVerticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().testTag("center-name"),
                    enabled = !busy, label = { Text(AppText.get(R.string.sports_center_dialog_nazvanie)) }, singleLine = false,
                    supportingText = { Text(AppText.get(R.string.sports_center_dialog_ne_bolee_200_simvolov)) })
                OutlinedTextField(city, { city = it }, Modifier.fillMaxWidth().testTag("center-city"),
                    enabled = !busy, label = { Text(AppText.get(R.string.sports_center_dialog_gorod_neobyazatelno)) }, singleLine = false)
                Text(AppText.get(R.string.sports_center_dialog_dostupnye_vidy_sporta), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sports.forEach { sport ->
                        FilterChip(selected = sport.id in selectedIds, enabled = !busy,
                            onClick = {
                                selectedIds = if (sport.id in selectedIds) selectedIds.filter { it != sport.id }.toIntArray()
                                else selectedIds + sport.id
                            }, label = { Text(sport.title) })
                    }
                }
                if (center != null) Text(AppText.get(R.string.sports_center_dialog_izmeneniya_deystvuyut_dlya_novyh_zapisey))
                if (center?.isArchived == true) Text(AppText.get(R.string.sports_center_dialog_tsentr_v_arhive_redaktirovanie_ne))
                error?.let { Text(it, Modifier.testTag("center-error"), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

/** Uses live directory state: switching sports cannot display a previous async query result. */
@Composable
fun TrainingCenterSelector(
    sports: List<SportSummaryUiModel>,
    centers: List<SportsCenterUiModel>,
    sportId: Int,
    centerId: Long,
    onSportSelected: (Int) -> Unit,
    onCenterSelected: (Long) -> Unit,
    onSaveCenter: (suspend (SaveSportsCenterInput) -> Long)?,
    enabled: Boolean = true,
    sportLocked: Boolean = false,
    retainedCenterId: Long? = null,
    onCreateCenter: (() -> Unit)? = null,
) {
    val available = centers.filter { it.id == retainedCenterId || !it.isArchived && it.sports.any { sport -> sport.id == sportId } }
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(available, sportId) {
        // The application seeds centers before emitting its first directory snapshot.
        // Do not destroy a restored selection while that initial snapshot is loading.
        if (centers.isEmpty()) return@LaunchedEffect
        if (available.none { it.id == centerId }) onCenterSelected(available.firstOrNull()?.id ?: 0L)
    }
    DropdownSelector(AppText.get(R.string.sports_center_dialog_vid_sporta), sports.firstOrNull { it.id == sportId }?.title ?: AppText.get(R.string.sports_center_dialog_vyberite_sport),
        sports, { it.title }, { next ->
            if (centers.none { it.id == centerId && !it.isArchived && it.sports.any { sport -> sport.id == next.id } })
                onCenterSelected(0L)
            onSportSelected(next.id)
        }, Modifier.testTag("training-sport"), enabled = enabled && !sportLocked)
    DropdownSelector(AppText.get(R.string.sports_center_dialog_sportivnyy_tsentr), available.firstOrNull { it.id == centerId }?.fullTitle ?: AppText.get(R.string.sports_center_dialog_vyberite_tsentr),
        available, { it.fullTitle }, { onCenterSelected(it.id) }, Modifier.testTag("training-center"), enabled = enabled)
    if (available.isEmpty()) {
        Text(AppText.get(R.string.sports_center_dialog_dlya_etogo_vida_sporta_net))
        if (onSaveCenter != null && sportId != 0) TextButton(enabled = enabled, onClick = { if (onCreateCenter != null) onCreateCenter() else creating = true }) { Text(AppText.get(R.string.sports_center_dialog_sozdat_tsentr)) }
    }
    if (creating && onSaveCenter != null) SportsCenterDialog(null, sports, { creating = false },
        onSaveCenter, { creating = false }, initialSportId = sportId)
}
