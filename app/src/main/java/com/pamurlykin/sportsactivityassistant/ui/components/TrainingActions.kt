package com.pamurlykin.sportsactivityassistant.ui.components

import kotlinx.coroutines.isActive

import com.pamurlykin.sportsactivityassistant.text.AppText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.TrainingEditSnapshot
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import kotlinx.coroutines.CancellationException
import java.util.UUID
import androidx.compose.ui.res.stringResource
import com.pamurlykin.sportsactivityassistant.R

@Composable
fun TrainingActions(sportSlug: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    if (SportEditors.find(sportSlug) != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onEdit) { Text(AppText.get(R.string.add_completed_training_dialog_izmenit_trenirovku)) }
        TextButton(onClick = onDelete) { Text(AppText.get(R.string.training_actions_udalit_trenirovku)) }
    }
}

/** The original revision survives restoration along with the form, preventing stale overwrites. */
@Composable
fun TrainingActionDialog(id: Long, deleting: Boolean, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val sports by viewModel.statisticsState.collectAsStateWithLifecycle()
    val centers by viewModel.sportsCenters.collectAsStateWithLifecycle()
    val statisticsRead by viewModel.statisticsReadState.collectAsStateWithLifecycle()
    val centersRead by viewModel.centersReadState.collectAsStateWithLifecycle()
    val loadFailureText = stringResource(R.string.load_workout_failed)
    var loadAttempt by rememberSaveable(id) { mutableIntStateOf(0) }
    var revision by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var sportId by rememberSaveable(id) { mutableIntStateOf(0) }
    var centerId by rememberSaveable(id) { mutableLongStateOf(0) }
    var error by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var deletingNow by rememberSaveable(id) { mutableStateOf(false) }
    val requestId = rememberSaveable(id) { UUID.randomUUID().toString() }
    LaunchedEffect(id, loadAttempt) {
        if (revision == null) {
            try {
                val snapshot = viewModel.loadTrainingForEdit(id)
                sportId = snapshot.input.sportId
                centerId = snapshot.input.complexId
                revision = snapshot.revision
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = loadFailureText }
        }
    }
    val snapshot = revision?.let { TrainingEditSnapshot.restore(id, sportId, centerId, it) }
    if (snapshot == null || sports.sports.isEmpty() || centers.isEmpty()) {
        AdaptiveAlertDialog(onDismissRequest = onDismiss, title = { Text(AppText.get(R.string.training_actions_trenirovka)) },
            text = { Column(Modifier.dialogVerticalScroll(rememberScrollState())) {
                ReadStateNotice(error != null || statisticsRead.failed || centersRead.failed, {
                    error = null; loadAttempt++; viewModel.retryReads()
                }, failureMessage = error)
            } },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
    } else if (!deleting) {
        AddCompletedTrainingDialog(sports.sports, snapshot.input.date, centers, onDismiss,
            onSave = { input, token -> viewModel.saveCompletedTraining(input, token, snapshot) },
            onSaveCenter = viewModel::saveSportsCenter, initial = snapshot.input, onSaved = onDismiss)
    } else {
        LaunchedEffect(deletingNow) {
            if (deletingNow) {
                try {
                    viewModel.deleteCompletedTraining(snapshot, requestId)
                    onDismiss()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: AppText.get(R.string.training_actions_ne_udalos_udalit_trenirovku) }
                finally { if (kotlinx.coroutines.currentCoroutineContext().isActive) deletingNow = false }
            }
        }
        AdaptiveAlertDialog(onDismissRequest = { if (!deletingNow) onDismiss() },
            title = { Text(AppText.get(R.string.training_actions_udalit_trenirovku_2)) },
            text = {
                Column(Modifier.dialogVerticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(AppText.get(R.string.details_pair, sports.sports.firstOrNull { it.id == sportId }?.title.orEmpty(), snapshot.input.date))
                    Text(centers.firstOrNull { it.id == centerId }?.fullTitle.orEmpty())
                    Text(AppText.get(R.string.training_actions_rezultat_i_vsya_ego_sportivnaya))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = !deletingNow, onClick = { if (!deletingNow) deletingNow = true }) {
                Text(if (deletingNow) AppText.get(R.string.training_actions_udalenie) else AppText.get(R.string.training_actions_udalit))
            } },
            dismissButton = { TextButton(enabled = !deletingNow, onClick = onDismiss) { Text(AppText.get(R.string.add_completed_training_dialog_otmena)) } })
    }
}
