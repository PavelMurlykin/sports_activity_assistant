package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.backup.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewContent(preview: ImportPreview, busy: Boolean, message: String?,
                         onChange: (ImportChoices) -> Unit, onApply: () -> Unit, onCancel: () -> Unit) {
    val choices = preview.choices
    Scaffold(topBar = { TopAppBar(title = { Text("Проверка импорта") }) }, bottomBar = {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Отмена") }
            Button(onClick = onApply, enabled = preview.canApply && !busy, modifier = Modifier.weight(1f)) { Text("Применить импорт") }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("${preview.format}: ${preview.trainings} тренировок, ${preview.routes} записей трасс, ${preview.plans} планов, ${preview.rules} серий.")
                Text("Пока ничего не записано. История на устройстве не удаляется. Повторный UUID пропускается, одинаковое содержание само по себе — нет.")
                if (busy) CircularProgressIndicator()
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (preview.profiles.size > 1) item {
                Text("Профиль из файла", style = MaterialTheme.typography.titleMedium)
                SelectImportOption(
                    label = if (choices.preserveAllProfiles) "Сохранить все отдельно" else preview.profiles.firstOrNull { it.publicId == choices.selectedProfile }?.title ?: "Требуется выбор",
                    enabled = !busy,
                    options = listOf("all" to "Сохранить все отдельно") + preview.profiles.map { it.publicId to "Только ${it.title} → текущий профиль" },
                ) { id -> onChange(choices.copy(preserveAllProfiles = id == "all", selectedProfile = id.takeUnless { it == "all" }, skipTrainingIds = emptySet(), keepLocalIds = emptySet())) }
            }
            items(preview.centers, key = { "center-${it.sourceId}" }) { center ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(center.title)
                        val targetTitle = preview.centerOptions.firstOrNull { it.publicId == center.target }?.title
                        val label = if (!center.resolved) "Подтвердите центр${targetTitle?.let { ": $it" } ?: ""}" else targetTitle ?: "Создать новый центр"
                        if (center.fixed) Text("$label · сопоставление сохранено")
                        else SelectImportOption(label, !busy, listOf("new" to "Создать новый: ${center.title}") + preview.centerOptions.map { it.publicId to it.title }) { id ->
                            onChange(choices.copy(centerMappings = choices.centerMappings + (center.sourceId to id.takeUnless { it == "new" })))
                        }
                    }
                }
            }
            items(preview.warnings) { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(preview.errors) { Text(it, color = MaterialTheme.colorScheme.error) }
            item {
                Text("Уже существуют по UUID: ${preview.records.count { it.exists }}. Возможные совпадения содержания: ${preview.records.count { it.possibleMatch }}. Конфликты: ${preview.records.count { it.conflict }}.")
            }
            items(preview.records.filter { it.conflict || it.possibleMatch }, key = { "record-${it.publicId}" }) { record ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(record.title)
                        Text("UUID ${record.publicId}", style = MaterialTheme.typography.bodySmall)
                        if (record.conflict) {
                            Text("Этот UUID уже существует с другими данными. Импорт его не перезапишет.")
                            Row {
                                Checkbox(record.publicId in choices.keepLocalIds, enabled = !busy, onCheckedChange = { checked ->
                                    onChange(choices.copy(keepLocalIds = if (checked) choices.keepLocalIds + record.publicId else choices.keepLocalIds - record.publicId))
                                })
                                Text("Сохранить локальную запись", Modifier.padding(top = 12.dp))
                            }
                        } else {
                            Text("Похожая тренировка с другим UUID: по умолчанию сохранить обе.")
                            Row {
                                Checkbox(record.publicId in choices.skipTrainingIds, enabled = !busy, onCheckedChange = { checked ->
                                    onChange(choices.copy(skipTrainingIds = if (checked) choices.skipTrainingIds + record.publicId else choices.skipTrainingIds - record.publicId))
                                })
                                Text("Пропустить запись из файла", Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectImportOption(label: String, enabled: Boolean, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Выберите соответствие") }, text = {
        LazyColumn(Modifier.heightIn(max = 400.dp)) {
            items(options, key = { it.first }) { (id, title) ->
                TextButton(onClick = { open = false; onSelect(id) }, modifier = Modifier.fillMaxWidth()) { Text(title) }
            }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Закрыть") } })
}
