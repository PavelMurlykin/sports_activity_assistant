package com.pamurlykin.sportsactivityassistant.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.SportsCenterUiModel
import com.pamurlykin.sportsactivityassistant.text.AppText
import com.pamurlykin.sportsactivityassistant.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val read by viewModel.sportPreferencesReadState.collectAsStateWithLifecycle()
    val centersRead by viewModel.centersReadState.collectAsStateWithLifecycle()
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
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            item { Text(stringResource(R.string.settings_backup_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
