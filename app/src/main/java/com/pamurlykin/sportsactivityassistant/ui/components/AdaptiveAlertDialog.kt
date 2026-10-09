package com.pamurlykin.sportsactivityassistant.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val LocalWholeDialogScroll = staticCompositionLocalOf { false }

@Composable
fun Modifier.dialogVerticalScroll(state: ScrollState): Modifier =
    if (LocalWholeDialogScroll.current) this else verticalScroll(state)

/** In landscape the entire dialog scrolls, including actions: even with IME and large
 * fonts its fields and actions remain reachable without nesting unbounded scrollers.
 */
@Composable
fun AdaptiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        title?.let { content ->
                            Box(Modifier.semantics { heading() }) {
                                ProvideTextStyle(MaterialTheme.typography.headlineSmall) { content() }
                            }
                        }
                        CompositionLocalProvider(LocalWholeDialogScroll provides true) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text?.invoke() }
                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            dismissButton?.invoke()
                            confirmButton()
                        }
                    }
                }
            }
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        modifier = modifier,
    )
}
