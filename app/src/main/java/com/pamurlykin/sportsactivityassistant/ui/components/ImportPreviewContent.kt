package com.pamurlykin.sportsactivityassistant.ui.components

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.backup.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewContent(preview: ImportPreview, busy: Boolean, message: String?,
                         onChange: (ImportChoices) -> Unit, onApply: () -> Unit, onCancel: () -> Unit) {
    val choices = preview.choices
    Scaffold(topBar = { TopAppBar(title = { Text(AppText.get(R.string.import_preview_content_proverka_importa)) }) }, bottomBar = {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f)) { Text(AppText.get(R.string.add_completed_training_dialog_otmena)) }
            Button(onClick = onApply, enabled = preview.canApply && !busy, modifier = Modifier.weight(1f)) { Text(AppText.get(R.string.import_preview_content_primenit_import)) }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(AppText.get(R.string.import_preview_content_trenirovok_zapisey_trass_planov, preview.format, preview.trainings, preview.routes, preview.plans, preview.rules))
                Text(AppText.get(R.string.import_preview_content_poka_nichego_ne_zapisano_istoriya))
                if (busy) CircularProgressIndicator()
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (preview.profiles.size > 1) item {
                Text(AppText.get(R.string.import_preview_content_profil_iz_fayla), style = MaterialTheme.typography.titleMedium)
                SelectImportOption(
                    label = if (choices.preserveAllProfiles) AppText.get(R.string.import_preview_content_sohranit_vse_otdelno) else preview.profiles.firstOrNull { it.publicId == choices.selectedProfile }?.title ?: AppText.get(R.string.import_preview_content_trebuetsya_vybor),
                    enabled = !busy,
                    options = listOf("all" to AppText.get(R.string.import_preview_content_sohranit_vse_otdelno)) + preview.profiles.map { it.publicId to AppText.get(R.string.import_preview_content_tolko_tekuschiy_profil, it.title) },
                ) { id -> onChange(choices.copy(preserveAllProfiles = id == "all", selectedProfile = id.takeUnless { it == "all" }, skipTrainingIds = emptySet(), keepLocalIds = emptySet())) }
            }
            items(preview.centers, key = { "center-${it.sourceId}" }) { center ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(center.title)
                        val targetTitle = preview.centerOptions.firstOrNull { it.publicId == center.target }?.title
                        val label = if (!center.resolved) AppText.get(R.string.import_preview_content_podtverdite_tsentr, targetTitle?.let { ": ${it}" } ?: "") else targetTitle ?: AppText.get(R.string.import_preview_content_sozdat_novyy_tsentr)
                        if (center.fixed) Text(AppText.get(R.string.import_preview_content_sopostavlenie_sohraneno, label))
                        else SelectImportOption(label, !busy, listOf("new" to AppText.get(R.string.import_preview_content_sozdat_novyy, center.title)) + preview.centerOptions.map { it.publicId to it.title }) { id ->
                            onChange(choices.copy(centerMappings = choices.centerMappings + (center.sourceId to id.takeUnless { it == "new" })))
                        }
                    }
                }
            }
            items(preview.warnings) { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(preview.errors) { Text(it, color = MaterialTheme.colorScheme.error) }
            item {
                Text(AppText.get(R.string.import_preview_content_uzhe_suschestvuyut_po_uuid, preview.records.count { it.exists }, preview.records.count { it.possibleMatch }, preview.records.count { it.conflict }))
            }
            items(preview.records.filter { it.conflict || it.possibleMatch }, key = { "record-${it.publicId}" }) { record ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(record.title)
                        Text(AppText.get(R.string.record_uuid, record.publicId), style = MaterialTheme.typography.bodySmall)
                        if (record.conflict) {
                            Text(AppText.get(R.string.import_preview_content_etot_uuid_uzhe_suschestvuet_s))
                            Row(Modifier.toggleable(record.publicId in choices.keepLocalIds, enabled = !busy, role = Role.Checkbox, onValueChange = { checked ->
                                    onChange(choices.copy(keepLocalIds = if (checked) choices.keepLocalIds + record.publicId else choices.keepLocalIds - record.publicId))
                                })) {
                                Checkbox(record.publicId in choices.keepLocalIds, onCheckedChange = null)
                                Text(AppText.get(R.string.import_preview_content_sohranit_lokalnuyu_zapis), Modifier.padding(top = 12.dp))
                            }
                        } else {
                            Text(AppText.get(R.string.import_preview_content_pohozhaya_trenirovka_s_drugim_uuid))
                            Row(Modifier.toggleable(record.publicId in choices.skipTrainingIds, enabled = !busy, role = Role.Checkbox, onValueChange = { checked ->
                                    onChange(choices.copy(skipTrainingIds = if (checked) choices.skipTrainingIds + record.publicId else choices.skipTrainingIds - record.publicId))
                                })) {
                                Checkbox(record.publicId in choices.skipTrainingIds, onCheckedChange = null)
                                Text(AppText.get(R.string.import_preview_content_propustit_zapis_iz_fayla), Modifier.padding(top = 12.dp))
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
    if (open) AdaptiveAlertDialog(onDismissRequest = { open = false }, title = { Text(AppText.get(R.string.import_preview_content_vyberite_sootvetstvie)) }, text = {
        LazyColumn(Modifier.heightIn(max = 400.dp)) {
            items(options, key = { it.first }) { (id, title) ->
                TextButton(onClick = { open = false; onSelect(id) }, modifier = Modifier.fillMaxWidth()) { Text(title) }
            }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text(AppText.get(R.string.import_preview_content_zakryt)) } })
}
