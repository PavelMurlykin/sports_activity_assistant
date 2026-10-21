package com.pamurlykin.sportsactivityassistant.ui.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.SportsCenterUiModel
import com.pamurlykin.sportsactivityassistant.data.model.ThemeMode
import com.pamurlykin.sportsactivityassistant.text.AppText
import com.pamurlykin.sportsactivityassistant.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val read by viewModel.sportPreferencesReadState.collectAsStateWithLifecycle()
    val centersRead by viewModel.centersReadState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dataState by viewModel.dataOperationState.collectAsStateWithLifecycle()
    val fileBusy by viewModel.fileBusy.collectAsStateWithLifecycle()
    val exportReady by viewModel.exportReady.collectAsStateWithLifecycle()
    val preview by viewModel.importPreview.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.applicationContext.contentResolver
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        viewModel.exportSelected(resolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.importSelected(resolver, uri)
    }
    LaunchedEffect(exportReady) {
        if (exportReady) {
            viewModel.exportLaunched()
            try { exportLauncher.launch("sports-activity-${LocalDate.now()}.json") }
            catch (e: Exception) { viewModel.reportDataError(e) }
        }
    }
    preview?.let {
        BackHandler { if (!dataState.inProgress) viewModel.cancelFile() }
        ImportPreviewContent(it, dataState.inProgress, dataState.message,
            viewModel::changeImportChoices, viewModel::confirmImport, viewModel::cancelFile)
        return
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun save(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { snackbar.showSnackbar(AppText.get(R.string.settings_save_error)) }
            finally { busy = false }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back)) }
        })
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("settings-list"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "appearance") {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp).selectableGroup()) {
                        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        ThemeMode.entries.forEach { mode ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = mode == themeMode, enabled = !busy, role = Role.RadioButton,
                                    onClick = { save { viewModel.setThemeMode(mode) } })
                                .testTag("theme-mode-${mode.storageValue}"), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = mode == themeMode, onClick = null, enabled = !busy)
                                Text(stringResource(when (mode) {
                                    ThemeMode.LIGHT -> R.string.settings_theme_light
                                    ThemeMode.DARK -> R.string.settings_theme_dark
                                    ThemeMode.SYSTEM -> R.string.settings_theme_system
                                }), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Text(stringResource(R.string.settings_theme_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text(stringResource(R.string.settings_sports), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_sports_hint), style = MaterialTheme.typography.bodyMedium)
            }
            if (read.failed || centersRead.failed) item { ReadStateNotice(true, viewModel::retryReads) }
            else if (read.data == null || centersRead.data == null) item { ReadStateNotice(false, viewModel::retryReads) }
            else items(read.data.orEmpty(), key = { it.sport.id }) { preference ->
                val sport = preference.sport
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SportBadge(sport.slug, size = 32.dp)
                            Text(sport.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(preference.isVisible, { visible -> save { viewModel.setSportVisible(sport.id, visible) } },
                                enabled = !busy, modifier = Modifier.testTag("sport-visible-${sport.slug}")
                                    .semantics { contentDescription = sport.title })
                        }
                        val available = centersRead.data.orEmpty().filter { !it.isArchived && it.sports.any { s -> s.id == sport.id } }
                        val primary = available.firstOrNull { sport.id in it.primarySportIds }
                        DropdownSelector(stringResource(R.string.settings_primary_center),
                            primary?.fullTitle ?: stringResource(R.string.primary_center_none),
                            listOf<SportsCenterUiModel?>(null) + available,
                            { it?.fullTitle ?: AppText.get(R.string.primary_center_none) },
                            { selected -> save { viewModel.setPrimaryCenter(sport.id, selected?.id) } },
                            Modifier.testTag("settings-primary-${sport.slug}"), enabled = !busy)
                    }
                }
            }
            dataState.message?.let { message -> item(key = "data-status") {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (dataState.inProgress) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(message, Modifier.weight(1f), color = if (dataState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        if (!dataState.inProgress) IconButton(onClick = viewModel::clearDataMessage) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.close))
                        }
                    }
                }
            } }
            item(key = "backup") {
                Card(Modifier.fillMaxWidth().testTag("settings-backup"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = viewModel::prepareExport, enabled = !fileBusy,
                            modifier = Modifier.fillMaxWidth().testTag("save-backup")) {
                            Icon(Icons.Rounded.SaveAlt, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_save_backup))
                        }
                        OutlinedButton(onClick = {
                            if (viewModel.beginImportSelection()) try {
                                importLauncher.launch(arrayOf("application/json", "text/csv", "text/*", "application/octet-stream"))
                            } catch (e: Exception) { viewModel.reportDataError(e) }
                        }, enabled = !fileBusy, modifier = Modifier.fillMaxWidth().testTag("restore-backup")) {
                            Icon(Icons.Rounded.Restore, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_restore_backup))
                        }
                    }
                }
            }
        }
    }
}
