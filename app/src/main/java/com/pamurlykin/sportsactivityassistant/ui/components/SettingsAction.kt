package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pamurlykin.sportsactivityassistant.R

@Composable
fun SettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings_title)) }
}
