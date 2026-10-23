package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.TrainingTimer
import kotlinx.coroutines.delay

val TrainingTimerSaver = listSaver<TrainingTimer, Long>(
    save = { listOf(it.accumulatedMillis, it.startedAtMillis ?: -1L) },
    restore = { TrainingTimer(it[0], it[1].takeIf { start -> start >= 0L }) },
)

@Composable
fun TrainingTimerRow(
    timer: TrainingTimer,
    clock: () -> Long,
    onTick: (Long) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    canStart: Boolean,
    canReset: Boolean,
    enabled: Boolean = true,
) {
    var elapsed by remember(timer) { mutableLongStateOf(timer.elapsedMillis(clock())) }
    val tick by rememberUpdatedState(onTick)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(timer, clock, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                elapsed = timer.elapsedMillis(clock())
                tick(elapsed)
                if (!timer.isRunning) break
                delay(250L)
            } while (true)
        }
    }
    val label = stringResource(R.string.training_timer)
    Row(Modifier.fillMaxWidth().testTag("training-timer"),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(TrainingTimer.display(elapsed),
            Modifier.weight(1f).testTag("training-timer-value").semantics { contentDescription = label },
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface), maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 24.sp, stepSize = 1.sp))
        FilledTonalIconButton(onClick = onStart, enabled = enabled && canStart && !timer.isRunning,
            modifier = Modifier.size(48.dp).testTag("training-timer-start")) {
            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.training_timer_start))
        }
        FilledTonalIconButton(onClick = onStop, enabled = enabled && timer.isRunning,
            modifier = Modifier.size(48.dp).testTag("training-timer-stop")) {
            Icon(Icons.Rounded.Stop, stringResource(R.string.training_timer_stop))
        }
        FilledTonalIconButton(onClick = onReset, enabled = enabled && canReset,
            modifier = Modifier.size(48.dp).testTag("training-timer-reset")) {
            Icon(Icons.Rounded.RestartAlt, stringResource(R.string.training_timer_reset))
        }
    }
}
