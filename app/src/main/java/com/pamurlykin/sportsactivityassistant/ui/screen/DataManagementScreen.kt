package com.pamurlykin.sportsactivityassistant.ui.screen

import android.net.Uri
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.dataOperationState.collectAsState()

    fun save(uri: Uri) {
        scope.launch {
            runCatching {
                val backup = viewModel.createBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                        stream.write(backup.toByteArray(Charsets.UTF_8))
                    } ?: error("Не удалось открыть файл для записи")
                }
            }.onSuccess { viewModel.reportBackupSaved() }.onFailure(viewModel::reportDataError)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(::save) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() }
                            ?: error("Не удалось прочитать выбранный файл")
                    }
                }.onSuccess(viewModel::importData).onFailure(viewModel::reportDataError)
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Данные", fontWeight = FontWeight.Bold) }) }) { padding ->
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
                        Text("Резервная копия", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Данные хранятся только на устройстве. JSON содержит центры, тренировки, спортивную статистику и планы календаря. Автоматический системный бэкап отключён.")
                        Text("Файл не зашифрован. Для работы без интернета выбирайте хранилище устройства в системном диалоге.")
                        Button(
                            onClick = { exportLauncher.launch("sports-activity-${LocalDate.now()}.json") },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Сохранить копию в файл") }
                    }
                }
            }
            item {
                Card {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Импорт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Поддерживаются резервные копии JSON и футбольная история из CSV. Совпадающие по содержанию тренировки пропускаются. Интернет и внешний аккаунт не нужны.")
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/csv", "text/*", "application/octet-stream")) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.inProgress,
                        ) { Text("Выбрать файл") }
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
                            OutlinedButton(onClick = viewModel::clearDataMessage) { Text("Закрыть") }
                        }
                    }
                }
            }
        }
    }
}
