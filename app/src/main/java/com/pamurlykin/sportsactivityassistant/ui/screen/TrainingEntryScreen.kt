package com.pamurlykin.sportsactivityassistant.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.AddCompletedTrainingDialog
import com.pamurlykin.sportsactivityassistant.ui.components.ReadStateNotice
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Composable
fun TrainingEntryScreen(
    viewModel: MainViewModel,
    initialDate: LocalDate,
    initialSportId: Int,
    trainingId: Long,
    planKey: String?,
    onBack: () -> Unit,
) {
    val centers by viewModel.sportsCenters.collectAsStateWithLifecycle()
    val sportsRead by viewModel.sportPreferencesReadState.collectAsStateWithLifecycle()
    val centersRead by viewModel.centersReadState.collectAsStateWithLifecycle()
    var revision by rememberSaveable { mutableStateOf<String?>(null) }
    var sportId by rememberSaveable { mutableIntStateOf(initialSportId) }
    var centerId by rememberSaveable { mutableLongStateOf(0L) }
    var planJson by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(trainingId, planKey, attempt) {
        try {
            if (trainingId != 0L && revision == null) {
                val snapshot = viewModel.loadTrainingForEdit(trainingId)
                sportId = snapshot.input.sportId; centerId = snapshot.input.complexId; revision = snapshot.revision
            } else if (planKey != null && planJson == null) {
                planJson = Json.encodeToString(PlanSnapshot.serializer(), viewModel.loadPlan(planKey, PlanScope.EVENT))
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    val snapshot = revision?.let { TrainingEditSnapshot.restore(trainingId, sportId, centerId, it) }
    val plan = planJson?.let { Json.decodeFromString(PlanSnapshot.serializer(), it) }
    val sports = sportsRead.data.orEmpty().filter { it.isVisible || it.sport.id == snapshot?.input?.sportId || it.sport.id == plan?.sportId }.map { it.sport }
    if (sportsRead.data == null || centersRead.data == null || sportsRead.failed || centersRead.failed ||
        trainingId != 0L && snapshot == null || planKey != null && plan == null) {
        TrainingFormScreen(onBack, {}, title = { Text(stringResource(R.string.add_completed_training_dialog_zapisat_trenirovku)) },
            text = { ReadStateNotice(failed || sportsRead.failed || centersRead.failed, {
                failed = false; attempt++; viewModel.retryReads()
            }) })
        return
    }
    if (sports.isEmpty()) {
        TrainingFormScreen(onBack, {}, title = { Text(stringResource(R.string.add_completed_training_dialog_zapisat_trenirovku)) },
            text = { Text(stringResource(R.string.settings_choose_sports)) })
        return
    }
    val duration = plan?.let { TrainingTimes.duration(it.startTime, it.endTime) }
    val initial = snapshot?.input ?: plan?.let {
        val football = sports.firstOrNull { s -> s.id == it.sportId }?.slug == "football"
        AddCompletedTrainingInput(it.sportId, it.complexId, LocalDate.parse(it.date),
            football = if (football) FootballTrainingInput(0, 0, 0, 0, null, null, duration) else null,
            durationMinutes = if (football) null else duration)
    }
    AddCompletedTrainingDialog(sports, initialDate, centers, onBack,
        onSave = { input, request ->
            if (plan != null) viewModel.completePlan(plan, input, request)
            else viewModel.saveCompletedTraining(input, request, snapshot)
        }, onSaveCenter = viewModel::saveSportsCenter, initial = initial, onSaved = onBack,
        fromPlan = plan != null, initialSportId = initialSportId.takeIf { id -> sports.any { it.id == id } }, plannedDurationMinutes = duration)
}

/** A regular navigation destination, with its actions available above the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingFormScreen(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    BackHandler(onBack = onDismissRequest)
    Scaffold(modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { title?.invoke() }, navigationIcon = {
            IconButton(onClick = onDismissRequest) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.add_completed_training_dialog_otmena))
            }
        })
    }, bottomBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End)) {
            dismissButton?.invoke(); confirmButton()
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) { text?.invoke() }
    }
}
