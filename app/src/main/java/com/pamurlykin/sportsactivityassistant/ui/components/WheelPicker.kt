package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pamurlykin.sportsactivityassistant.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import java.math.BigDecimal
import kotlin.math.abs

@Composable
fun WorkoutCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color.White), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF0F5F6)))).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

private fun Modifier.pickerSemantics(label: String, options: List<String>, index: Int, enabled: Boolean, onSelected: (Int) -> Unit) =
    semantics(mergeDescendants = true) {
        contentDescription = label
        stateDescription = options[index]
        progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..options.lastIndex.toFloat(), (options.size - 2).coerceAtLeast(0))
        if (enabled) setProgress { onSelected(it.toInt().coerceIn(options.indices)); true } else disabled()
    }

/** Three visible rows with snap-to-center and a shaded mechanical-drum finish. */
@Composable
private fun DrumWheel(options: List<String>, index: Int, onSelected: (Int) -> Unit, modifier: Modifier, enabled: Boolean, tag: String? = null, cyclic: Boolean = false) {
    val anchor = if (cyclic) options.size * 500 else 0
    val count = if (cyclic) options.size * 1001 else options.size
    val list = rememberLazyListState(initialFirstVisibleItemIndex = anchor + index)
    val callback by rememberUpdatedState(onSelected)
    val currentIndex by rememberUpdatedState(index)
    fun centerIndex(): Int {
        val layout = list.layoutInfo
        val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
        return layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: (anchor + currentIndex)
    }
    LaunchedEffect(options) {
        snapshotFlow { centerIndex() % options.size }.distinctUntilChanged().drop(1).collect { callback(it) }
    }
    LaunchedEffect(index, options) {
        if (!list.isScrollInProgress && centerIndex() % options.size != index) {
            val current = centerIndex()
            var target = if (cyclic) current - current % options.size + index else index
            if (cyclic && target - current > options.size / 2) target -= options.size
            if (cyclic && current - target > options.size / 2) target += options.size
            list.scrollToItem(target.coerceIn(0, count - 1))
        }
    }
    val frame = RoundedCornerShape(15.dp)
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFFB8C2C8), Color(0xFFF3F6F7), Color(0xFF9CA8AE))), frame).padding(3.dp)) {
        Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF070B0E), Color(0xFF343E43), Color(0xFF070B0E))))) {
            LazyColumn(state = list, flingBehavior = rememberSnapFlingBehavior(list), userScrollEnabled = enabled,
                modifier = Modifier.fillMaxSize().then(if (tag != null) Modifier.testTag(tag) else Modifier),
                contentPadding = PaddingValues(vertical = 44.dp)) {
                items(count = count, key = { it }) { position ->
                    val i = position % options.size
                    val value = options[i]
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                        Text(value, color = if (i == index) Color.White else Color(0xFF859096).copy(alpha = 0.65f),
                            fontSize = if (options.all { it.length == 1 }) 38.sp else 30.sp,
                            lineHeight = 42.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            HorizontalDivider(Modifier.align(Alignment.Center).offset(y = (-22).dp), color = Color.White.copy(alpha = 0.14f))
            HorizontalDivider(Modifier.align(Alignment.Center).offset(y = 22.dp), color = Color.Black.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun WheelPicker(label: String, options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (options.isEmpty()) return
    val index = selectedIndex.coerceIn(options.indices)
    Column(modifier.pickerSemantics(label, options, index, enabled, onSelected), verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        DrumWheel(options, index, onSelected, Modifier.widthIn(max = 160.dp).fillMaxWidth(), enabled)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ScoreWheelPicker(label: String, value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tag: String) {
    // A legacy score above 99 remains editable without silently truncating its value.
    val options = (0..99).map(Int::toString) + listOfNotNull(value.takeIf { it > 99 }?.toString())
    val index = options.indexOf(value.toString()).coerceAtLeast(0)
    Column(modifier.testTag(tag).pickerSemantics(label, options, index, enabled) { onValueChange(options[it].toInt()) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (value > 99) DrumWheel(options, index, { onValueChange(options[it].toInt()) }, Modifier.fillMaxWidth(), enabled)
        else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val digits = (0..9).map(Int::toString)
            DrumWheel(digits, value / 10, { onValueChange(it * 10 + value % 10) }, Modifier.weight(1f), enabled, "$tag-tens", cyclic = true)
            DrumWheel(digits, value % 10, { onValueChange(value / 10 * 10 + it) }, Modifier.weight(1f), enabled, "$tag-units", cyclic = true)
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val inset = 2.dp.toPx()
            val path = Path().apply {
                moveTo(inset, 0f); lineTo(inset, size.height / 2); quadraticTo(inset, size.height, inset * 3, size.height)
                lineTo(size.width - inset * 3, size.height); quadraticTo(size.width - inset, size.height, size.width - inset, size.height / 2)
                lineTo(size.width - inset, 0f)
            }
            drawPath(path, Color(0xFF8A949C), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, minLines = 2)
    }
}

@Composable
fun StepNumberField(value: String, onValueChange: (String) -> Unit, label: String, tag: String,
    error: String? = null, enabled: Boolean = true, decimal: Boolean = false, increment: Int = 1) {
    fun step(direction: Int) {
        val current = value.trim().replace(',', '.').toBigDecimalOrNull()
            ?.takeIf { it.precision() <= 16 && it.scale() in -22..6 } ?: BigDecimal.ZERO
        val delta = if (decimal) BigDecimal("0.1") else increment.toBigDecimal()
        val minimum = if (decimal) BigDecimal.ZERO else BigDecimal.ONE
        onValueChange((current + delta * direction.toBigDecimal()).coerceAtLeast(minimum).stripTrailingZeros().toPlainString())
    }
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().testTag(tag), enabled = enabled,
        label = { Text(label) }, singleLine = true, isError = error != null, shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        trailingIcon = { Row(Modifier.padding(end = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalIconButton(onClick = { step(-1) }, enabled = enabled, modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.decrement_value, label))
            }
            FilledTonalIconButton(onClick = { step(1) }, enabled = enabled, modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.Add, stringResource(R.string.increment_value, label))
            }
        } })
}
