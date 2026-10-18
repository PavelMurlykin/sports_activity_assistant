package com.pamurlykin.sportsactivityassistant.ui.components

import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pamurlykin.sportsactivityassistant.R
import java.math.BigDecimal

@Composable
fun WheelPicker(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return
    val index = selectedIndex.coerceIn(options.indices)
    Column(modifier.semantics(mergeDescendants = true) {
        contentDescription = label
        stateDescription = options[index]
        progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..options.lastIndex.toFloat(), (options.size - 2).coerceAtLeast(0))
        if (enabled) setProgress { next -> onSelected(next.toInt().coerceIn(options.indices)); true } else disabled()
    }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, minLines = 2)
        AndroidView(
            factory = { context -> NumberPicker(context).apply {
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                wrapSelectorWheel = false
            } },
            modifier = Modifier.fillMaxWidth().height(132.dp),
            update = { picker ->
                picker.setOnValueChangedListener(null)
                picker.displayedValues = null
                picker.minValue = 0
                picker.maxValue = options.lastIndex
                picker.displayedValues = options.toTypedArray()
                picker.value = index
                picker.isEnabled = enabled
                picker.setOnValueChangedListener { _, _, next -> onSelected(next) }
            },
        )
    }
}

@Composable
fun StepNumberField(
    value: String, onValueChange: (String) -> Unit, label: String, tag: String,
    error: String? = null, enabled: Boolean = true, decimal: Boolean = false,
) {
    fun step(direction: Int) {
        val current = value.trim().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
        val delta = if (decimal) BigDecimal("0.1") else BigDecimal.ONE
        val minimum = if (decimal) BigDecimal.ZERO else BigDecimal.ONE
        onValueChange((current + delta * direction.toBigDecimal()).coerceAtLeast(minimum).stripTrailingZeros().toPlainString())
    }
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().testTag(tag), enabled = enabled,
        label = { Text(label) }, singleLine = true, isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        trailingIcon = { Row {
            IconButton(onClick = { step(-1) }, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.decrement_value, label))
            }
            IconButton(onClick = { step(1) }, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Add, stringResource(R.string.increment_value, label))
            }
        } })
}
