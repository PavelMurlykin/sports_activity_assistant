package com.pamurlykin.sportsactivityassistant.ui.components

import kotlinx.coroutines.isActive

import com.pamurlykin.sportsactivityassistant.text.AppText

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pamurlykin.sportsactivityassistant.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import java.util.UUID

enum class PlanAction { EDIT, CANCEL, COMPLETE }

@Composable
fun PlanActionDialog(key: String, recurring: Boolean, action: PlanAction, viewModel: MainViewModel, onDismiss: () -> Unit) {
    var scope by rememberSaveable { mutableStateOf<PlanScope?>(if (recurring && action != PlanAction.COMPLETE) null else PlanScope.EVENT) }
    var encoded by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val requestId = rememberSaveable { UUID.randomUUID().toString() }
    val statistics by viewModel.statisticsState.collectAsStateWithLifecycle()
    val centers by viewModel.sportsCenters.collectAsStateWithLifecycle()
    val statisticsRead by viewModel.statisticsReadState.collectAsStateWithLifecycle()
    val centersRead by viewModel.centersReadState.collectAsStateWithLifecycle()
    val loadFailureText = stringResource(R.string.load_plan_failed)
    var loadAttempt by rememberSaveable { mutableIntStateOf(0) }
    if (scope == null) {
        AdaptiveAlertDialog(onDismissRequest = onDismiss, title = { Text(AppText.get(R.string.plan_actions_povtoryayuschayasya_trenirovka)) },
            text = { Text(if (action == PlanAction.EDIT)
                AppText.get(R.string.plan_actions_izmenit_tolko_eto_sobytie_ili)
                else AppText.get(R.string.plan_actions_otmenit_tolko_eto_sobytie_ili)) },
            confirmButton = { TextButton(onClick = { scope = PlanScope.EVENT }) { Text(AppText.get(R.string.plan_actions_tolko_eto_sobytie)) } },
            dismissButton = { Column {
                TextButton(onClick = { scope = PlanScope.SERIES }) { Text(AppText.get(R.string.plan_actions_vsya_seriya)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            } })
        return
    }
    LaunchedEffect(key, scope, loadAttempt) {
        if (encoded == null && error == null) {
            try { encoded = Json.encodeToString(PlanSnapshot.serializer(), viewModel.loadPlan(key, requireNotNull(scope))) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = loadFailureText }
        }
    }
    val snapshot = encoded?.let { Json.decodeFromString(PlanSnapshot.serializer(), it) }
    if (snapshot == null || statistics.sports.isEmpty() || centers.isEmpty()) {
        AdaptiveAlertDialog(onDismissRequest = onDismiss, title = { Text(AppText.get(R.string.add_training_dialog_plan_trenirovki)) },
            text = { Column(Modifier.dialogVerticalScroll(rememberScrollState())) {
                ReadStateNotice(error != null || statisticsRead.failed || centersRead.failed, {
                    error = null; loadAttempt++; viewModel.retryReads()
                }, failureMessage = error)
            } },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
        return
    }
    when (action) {
        PlanAction.EDIT -> AddPlannedTrainingDialog(statistics.sports, centers, onDismiss,
            { input, token -> viewModel.updatePlan(snapshot, requireNotNull(scope), input, token) },
            viewModel::saveSportsCenter, initial = snapshot.input(), onSaved = onDismiss)
        PlanAction.COMPLETE -> AddCompletedTrainingDialog(statistics.sports, java.time.LocalDate.parse(snapshot.date),
            centers, onDismiss, { input, token -> viewModel.completePlan(snapshot, input, token) },
            viewModel::saveSportsCenter,
            initial = AddCompletedTrainingInput(snapshot.sportId, snapshot.complexId, java.time.LocalDate.parse(snapshot.date)),
            onSaved = onDismiss, fromPlan = true)
        PlanAction.CANCEL -> {
            LaunchedEffect(saving) {
                if (saving) {
                    try { viewModel.cancelPlan(snapshot, requireNotNull(scope), requestId); onDismiss() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message }
                    finally { if (kotlinx.coroutines.currentCoroutineContext().isActive) saving = false }
                }
            }
            AdaptiveAlertDialog(onDismissRequest = { if (!saving) onDismiss() },
                title = { Text(if (scope == PlanScope.SERIES) AppText.get(R.string.plan_actions_otmenit_vsyu_seriyu) else AppText.get(R.string.plan_actions_otmenit_trenirovku)) },
                text = { Column(Modifier.dialogVerticalScroll(rememberScrollState())) {
                    Text(if (scope == PlanScope.SERIES) AppText.get(R.string.plan_actions_vse_nezavershyonnye_sobytiya_serii_vklyuchaya)
                        else AppText.get(R.string.plan_actions_sobytie_ostanetsya_v_kalendare_s, snapshot.date))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } },
                confirmButton = { TextButton(enabled = !saving, onClick = { saving = true }) { Text(if (saving) AppText.get(R.string.plan_actions_otmena) else AppText.get(R.string.plan_actions_podtverdit_otmenu)) } },
                dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(AppText.get(R.string.plan_actions_ostavit_plan)) } })
        }
    }
}
