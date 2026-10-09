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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCompletedTrainingDialog(
    sports: List<SportSummaryUiModel>,
    initialDate: LocalDate,
    centers: List<SportsCenterUiModel>,
    onDismiss: () -> Unit,
    onSave: suspend (AddCompletedTrainingInput, String) -> Unit,
    onSaveCenter: (suspend (SaveSportsCenterInput) -> Long)? = null,
    initial: AddCompletedTrainingInput? = null,
    onSaved: () -> Unit = {},
    fromPlan: Boolean = false,
) {
    var selectedSportId by rememberSaveable { mutableIntStateOf(initial?.sportId ?: sports.firstOrNull()?.id ?: 0) }
    var selectedComplexId by rememberSaveable { mutableLongStateOf(initial?.complexId ?: 0L) }
    var dateText by rememberSaveable { mutableStateOf((initial?.date ?: initialDate).toString()) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var creatingCenter by rememberSaveable { mutableStateOf(false) }
    val requestId = rememberSaveable { UUID.randomUUID().toString() }
    val holders = rememberSaveableStateHolder()
    LaunchedEffect(sports) {
        if (selectedSportId == 0 && sports.isNotEmpty()) selectedSportId = sports.first().id
    }
    val selectedSport = sports.firstOrNull { it.id == selectedSportId }
    val dateResult = runCatching {
        TrainingValidation.parseDate(dateText.trim()).also { TrainingValidation.completedDate(it, initial?.date.takeUnless { fromPlan }) }
    }
    val dateError = if (!attempted || dateResult.isSuccess) null else {
        if (runCatching { TrainingValidation.parseDate(dateText.trim()) }.isFailure) AppText.get(R.string.add_completed_training_dialog_vvedite_datu_yyyy_mm_dd_god_0001_9999)
        else dateResult.exceptionOrNull()?.message
    }
    holders.SaveableStateProvider(selectedSportId) {
        val draft = SportEditors.find(selectedSport?.slug)?.rememberDraft(initial.takeUnless { fromPlan })
        fun input(): AddCompletedTrainingInput {
            val date = dateResult.getOrElse { throw IllegalArgumentException(dateError ?: AppText.get(R.string.add_completed_training_dialog_proverte_datu)) }
            require(selectedSport != null && centers.any { center ->
                center.id == selectedComplexId && (!fromPlan && center.id == initial?.complexId ||
                    !center.isArchived && center.sports.any { it.id == selectedSportId })
            }) { AppText.get(R.string.add_completed_training_dialog_vyberite_vid_sporta_i_sportivnyy) }
            return requireNotNull(draft) { AppText.get(R.string.add_completed_training_dialog_dlya_etogo_vida_sporta_forma) }
                .input(selectedSportId, selectedComplexId, date)
        }
        LaunchedEffect(saving, selectedSport?.id, centers.isNotEmpty()) {
            if (saving && selectedSport != null && centers.isNotEmpty()) {
                try {
                    onSave(input(), requestId)
                    errorText = null
                    onSaved()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { errorText = e.message ?: AppText.get(R.string.add_completed_training_dialog_ne_udalos_sohranit_trenirovku) }
                finally { if (kotlinx.coroutines.currentCoroutineContext().isActive) saving = false }
            }
        }
        AdaptiveAlertDialog(
            modifier = Modifier.imePadding(),
            onDismissRequest = { if (!saving) onDismiss() },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    if (!saving) {
                        attempted = true
                        runCatching { input() }.onSuccess { errorText = null; saving = true }
                            .onFailure { errorText = it.message ?: AppText.get(R.string.add_completed_training_dialog_proverte_dannye_trenirovki) }
                    }
                }) { Text(if (saving) AppText.get(R.string.add_completed_training_dialog_sohranenie) else AppText.get(R.string.add_completed_training_dialog_sohranit)) }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(AppText.get(R.string.add_completed_training_dialog_otmena)) } },
            title = { Text(if (fromPlan) AppText.get(R.string.add_completed_training_dialog_rezultat_po_planu) else if (initial == null) AppText.get(R.string.add_completed_training_dialog_zapisat_trenirovku) else AppText.get(R.string.add_completed_training_dialog_izmenit_trenirovku)) },
            text = {
                Column(Modifier.dialogVerticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TrainingCenterSelector(sports, centers, selectedSportId, selectedComplexId,
                        { selectedSportId = it }, { selectedComplexId = it }, onSaveCenter,
                        enabled = !saving, sportLocked = initial != null, retainedCenterId = initial?.complexId.takeUnless { fromPlan },
                        onCreateCenter = { creatingCenter = true })
                    if (initial != null) Text(if (fromPlan) AppText.get(R.string.add_completed_training_dialog_vid_sporta_iz_plana_ukazhite) else AppText.get(R.string.add_completed_training_dialog_vid_sporta_menyat_nelzya_prezhniy),
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(dateText, { dateText = it }, Modifier.fillMaxWidth().testTag("training-date"),
                        enabled = !saving, label = { Text(AppText.get(R.string.add_completed_training_dialog_data)) },
                        supportingText = { Text(dateError ?: AppText.get(R.string.add_completed_training_dialog_yyyy_mm_dd_buduschie_trenirovki_dobavlyayte)) },
                        isError = dateError != null, singleLine = true)
                    TextButton(enabled = !saving, onClick = { datePickerOpen = true },
                        modifier = Modifier.testTag("training-date-picker")) { Text(AppText.get(R.string.add_completed_training_dialog_vybrat_datu)) }
                    HorizontalDivider()
                    draft?.Content(enabled = !saving) ?: Text(AppText.get(R.string.add_completed_training_dialog_zapis_dlya_etogo_vida_sporta))
                    errorText?.let { Text(it, Modifier.testTag("training-error"),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
        )
    }
    if (creatingCenter && onSaveCenter != null) {
        SportsCenterDialog(null, sports, { creatingCenter = false }, onSaveCenter,
            { id -> selectedComplexId = id; creatingCenter = false }, initialSportId = selectedSportId)
    }
    if (datePickerOpen) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = dateResult.getOrNull()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            yearRange = 1..9999,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return date <= LocalDate.now() || !fromPlan && date == initial?.date
                }
            },
        )
        DatePickerDialog(onDismissRequest = { datePickerOpen = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                dateText = Instant.ofEpochMilli(requireNotNull(picker.selectedDateMillis)).atZone(ZoneOffset.UTC).toLocalDate().toString()
                datePickerOpen = false
            }) { Text(AppText.get(R.string.add_completed_training_dialog_vybrat)) } },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text(AppText.get(R.string.add_completed_training_dialog_otmena)) } }) {
            DatePicker(picker)
        }
    }
}
