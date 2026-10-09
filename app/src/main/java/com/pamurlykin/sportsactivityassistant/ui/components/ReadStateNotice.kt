package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.R

@Composable
fun ReadStateNotice(failed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier, failureMessage: String? = null) {
    Card(modifier.fillMaxWidth().testTag(if (failed) "read-error" else "read-loading")) {
        Column(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (failed) {
                Text(failureMessage ?: stringResource(R.string.read_failed), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.read_retry_help))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            } else {
                CircularProgressIndicator()
                Text(stringResource(R.string.loading_data))
            }
        }
    }
}
