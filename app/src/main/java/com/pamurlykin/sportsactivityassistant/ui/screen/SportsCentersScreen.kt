package com.pamurlykin.sportsactivityassistant.ui.screen

import kotlinx.coroutines.isActive

import com.pamurlykin.sportsactivityassistant.ui.components.dialogVerticalScroll

import com.pamurlykin.sportsactivityassistant.ui.components.AdaptiveAlertDialog

import com.pamurlykin.sportsactivityassistant.text.AppText

import androidx.compose.foundation.layout.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.SportsCenterUiModel
import com.pamurlykin.sportsactivityassistant.ui.components.SportsCenterDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.res.stringResource
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.ui.components.ReadStateNotice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsCentersScreen(viewModel: MainViewModel) {
    val centersRead by viewModel.centersReadState.collectAsStateWithLifecycle()
    val centers by viewModel.sportsCenters.collectAsStateWithLifecycle()
    val statisticsRead by viewModel.statisticsReadState.collectAsStateWithLifecycle()
    val statistics by viewModel.statisticsState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var archiveId by rememberSaveable { mutableStateOf<Long?>(null) }
    var archiveBusy by rememberSaveable { mutableStateOf(false) }
    var archiveError by rememberSaveable { mutableStateOf<String?>(null) }
    var archiveTarget by rememberSaveable { mutableStateOf(false) }
    var archiveRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(archiveBusy, archiveId) {
        val id = archiveId
        if (archiveBusy && id != null) {
            try {
                viewModel.setSportsCenterArchived(id, archiveTarget, requireNotNull(archiveRequestId))
                archiveBusy = false
                archiveId = null
                scope.launch { snackbar.showSnackbar(if (archiveTarget) AppText.get(R.string.sports_centers_screen_tsentr_v_arhive)
                    else AppText.get(R.string.sports_centers_screen_tsentr_vozvraschyon)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { archiveError = e.message ?: AppText.get(R.string.sports_centers_screen_ne_udalos_izmenit_tsentr) }
            finally { if (kotlinx.coroutines.currentCoroutineContext().isActive) archiveBusy = false }
        }
    }
    if (showDialog && (editedId == null || centers.any { it.id == editedId })) SportsCenterDialog(
        center = centers.firstOrNull { it.id == editedId }, sports = statistics.sports,
        onDismiss = { showDialog = false }, onSave = viewModel::saveSportsCenter,
        onSaved = { showDialog = false; scope.launch { snackbar.showSnackbar(AppText.get(R.string.sports_centers_screen_sportivnyy_tsentr_sohranyon)) } },
    )
    centers.firstOrNull { it.id == archiveId }?.let { center ->
        AdaptiveAlertDialog(
            onDismissRequest = { if (!archiveBusy) archiveId = null },
            title = { Text(if (center.isArchived) AppText.get(R.string.sports_centers_screen_vernut_tsentr) else AppText.get(R.string.sports_centers_screen_arhivirovat_tsentr)) },
            text = { Column(Modifier.dialogVerticalScroll(rememberScrollState())) {
                Text(center.fullTitle)
                Text(if (center.isArchived) AppText.get(R.string.sports_centers_screen_tsentr_snova_budet_dostupen_dlya)
                    else AppText.get(R.string.sports_centers_screen_novye_trenirovki_i_plany_zdes))
                archiveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !archiveBusy, onClick = {
                archiveBusy = true
                archiveRequestId = java.util.UUID.randomUUID().toString()
            }) { Text(if (center.isArchived) AppText.get(R.string.sports_centers_screen_vernut) else AppText.get(R.string.sports_centers_screen_v_arhiv)) } },
            dismissButton = { TextButton(enabled = !archiveBusy, onClick = { archiveId = null }) { Text(AppText.get(R.string.add_completed_training_dialog_otmena)) } },
        )
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Column {
            Text(AppText.get(R.string.sports_centers_screen_sportivnye_tsentry), fontWeight = FontWeight.Bold)
            if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE)
                Text(AppText.get(R.string.sports_centers_screen_mesta_i_dostupnye_vidy_sporta), style = MaterialTheme.typography.bodySmall)
        } }) },
        floatingActionButton = {
            if (centersRead.data != null && statisticsRead.data != null) {
                FloatingActionButton(onClick = { editedId = null; showDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_center))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showArchive, { showArchive = false }, label = { Text(AppText.get(R.string.sports_centers_screen_aktivnye)) })
                FilterChip(showArchive, { showArchive = true }, label = { Text(AppText.get(R.string.sports_centers_screen_arhiv)) })
            } }
            val visible = centers.filter { it.isArchived == showArchive }
            if (centersRead.failed || statisticsRead.failed) item { ReadStateNotice(true, viewModel::retryReads) }
            else if (centersRead.loading || statisticsRead.loading) item { ReadStateNotice(false, viewModel::retryReads) }
            else if (visible.isEmpty()) item { Text(if (showArchive) AppText.get(R.string.sports_centers_screen_arhiv_pust) else AppText.get(R.string.sports_centers_screen_dobavte_sportivnyy_tsentr)) }
            items(visible, key = { it.id }) { center ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(center.fullTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(center.sports.joinToString { it.title }.ifBlank { AppText.get(R.string.sports_centers_screen_vidy_sporta_ne_ukazany) })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editedId = center.id; showDialog = true }) { Text(AppText.get(R.string.sports_centers_screen_izmenit)) }
                            TextButton(onClick = { archiveError = null; archiveTarget = !center.isArchived; archiveId = center.id }) {
                                Text(if (center.isArchived) AppText.get(R.string.sports_centers_screen_vernut) else AppText.get(R.string.sports_centers_screen_v_arhiv))
                            }
                        }
                    }
                }
            }
        }
    }
}
