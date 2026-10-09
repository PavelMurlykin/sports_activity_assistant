package com.pamurlykin.sportsactivityassistant.ui.screen

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.pamurlykin.sportsactivityassistant.ui.components.ImportPreviewContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.dataOperationState.collectAsStateWithLifecycle()
    val busy by viewModel.fileBusy.collectAsStateWithLifecycle()
    val ready by viewModel.exportReady.collectAsStateWithLifecycle()
    val preview by viewModel.importPreview.collectAsStateWithLifecycle()
    val resolver = context.applicationContext.contentResolver
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        viewModel.exportSelected(resolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.importSelected(resolver, uri)
    }
    LaunchedEffect(ready) {
        if (ready) {
            viewModel.exportLaunched()
            try { exportLauncher.launch("sports-activity-${LocalDate.now()}.json") }
            catch (e: Exception) { viewModel.reportDataError(e) }
        }
    }
    preview?.let {
        ImportPreviewContent(it, state.inProgress, state.message, viewModel::changeImportChoices, viewModel::confirmImport, viewModel::cancelFile)
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text(AppText.get(R.string.data_management_screen_dannye), fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(AppText.get(R.string.data_management_screen_rezervnaya_kopiya), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(AppText.get(R.string.data_management_screen_dannye_hranyatsya_tolko_na_ustroystve))
                        Text(AppText.get(R.string.data_management_screen_fayl_ne_zashifrovan_dlya_raboty))
                        Button(
                            onClick = viewModel::prepareExport,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(AppText.get(R.string.data_management_screen_sohranit_kopiyu_v_fayl)) }
                    }
                }
            }
            item {
                Card {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(AppText.get(R.string.data_management_screen_import), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(AppText.get(R.string.data_management_screen_json_versiy_1_6_i_futbolnyy))
                        OutlinedButton(
                            onClick = {
                                if (viewModel.beginImportSelection()) try {
                                    importLauncher.launch(arrayOf("application/json", "text/csv", "text/*", "application/octet-stream"))
                                } catch (e: Exception) { viewModel.reportDataError(e) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                        ) { Text(AppText.get(R.string.data_management_screen_vybrat_fayl)) }
                    }
                }
            }
            state.message?.let { message ->
                item {
                    Card {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (state.inProgress) CircularProgressIndicator()
                            Text(
                                message,
                                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            OutlinedButton(onClick = viewModel::clearDataMessage, enabled = !state.inProgress) { Text(AppText.get(R.string.import_preview_content_zakryt)) }
                        }
                    }
                }
            }
        }
    }
}
