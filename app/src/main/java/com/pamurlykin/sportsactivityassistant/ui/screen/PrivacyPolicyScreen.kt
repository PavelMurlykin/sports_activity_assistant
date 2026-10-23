package com.pamurlykin.sportsactivityassistant.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val sections = listOf(
        R.string.privacy_section_data_title to R.string.privacy_section_data_body,
        R.string.privacy_section_purpose_title to R.string.privacy_section_purpose_body,
        R.string.privacy_section_network_title to R.string.privacy_section_network_body,
        R.string.privacy_section_files_title to R.string.privacy_section_files_body,
        R.string.privacy_section_storage_title to R.string.privacy_section_storage_body,
        R.string.privacy_section_security_title to R.string.privacy_section_security_body,
        R.string.privacy_section_contact_title to R.string.privacy_section_contact_body,
        R.string.privacy_section_changes_title to R.string.privacy_section_changes_body,
    )
    Scaffold(topBar = {
        TopAppBar(title = {
            Text(stringResource(R.string.privacy_policy_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back))
            }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("privacy-policy-list"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(key = "introduction") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.privacy_policy_title), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.privacy_policy_updated), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.privacy_policy_intro), style = MaterialTheme.typography.bodyMedium)
                }
            }
            sections.forEach { (title, body) -> item(key = title) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() })
                    SelectionContainer { Text(stringResource(body), style = MaterialTheme.typography.bodyMedium) }
                    if (title == R.string.privacy_section_contact_title) SelectionContainer {
                        Column(Modifier.testTag("privacy-policy-contact"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.developer_name), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.developer_email), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } }
        }
    }
}
