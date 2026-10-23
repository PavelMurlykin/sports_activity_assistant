package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.pamurlykin.sportsactivityassistant.R

@Composable
fun AboutAppCard(onPrivacyPolicy: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName.orEmpty() to PackageInfoCompat.getLongVersionCode(info)
    }
    Card(Modifier.fillMaxWidth().testTag("settings-about"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_version, version.first, version.second),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("about-version"))
            Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyMedium)
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.about_developer, stringResource(R.string.developer_name)),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_support, stringResource(R.string.developer_email)),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = onPrivacyPolicy, modifier = Modifier.fillMaxWidth().testTag("open-privacy-policy")) {
                Text(stringResource(R.string.privacy_policy_title))
            }
        }
    }
}
