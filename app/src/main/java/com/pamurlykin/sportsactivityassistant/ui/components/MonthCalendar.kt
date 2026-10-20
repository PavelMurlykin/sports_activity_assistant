package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthCalendar(
    state: ScheduleMonthUiModel,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 460.dp,
    collapseFraction: Float = 0f,
) {
    val previous by rememberUpdatedState(onPreviousMonth)
    val next by rememberUpdatedState(onNextMonth)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val compact = collapseFraction > 0.5f
    Card(modifier.fillMaxWidth().height(height).testTag("month-calendar").pointerInput(threshold) {
        var distance = 0f
        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onHorizontalDrag = { change, delta ->
            change.consume(); distance += delta
        }, onDragEnd = { if (distance < -threshold) next() else if (distance > threshold) previous() })
    }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = if (compact) 2.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 4.dp)) {
            Row(Modifier.fillMaxWidth().height(if (compact) 40.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.previous_month), tint = MaterialTheme.colorScheme.primary) }
                Text(state.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru"))
                    .replaceFirstChar { it.uppercase() } + " " + state.month.year,
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                IconButton(onClick = onNextMonth, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.next_month), tint = MaterialTheme.colorScheme.primary) }
            }
            if (!compact) Text(stringResource(R.string.calendar_legend), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Row(Modifier.fillMaxWidth()) {
                stringArrayResource(R.array.calendar_weekdays).forEach { name ->
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                }
            }
            state.days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { day ->
                        key(day.date) {
                            CalendarDayCell(day, day.date == state.selectedDate, { onDaySelected(day.date) },
                                Modifier.weight(1f).fillMaxHeight(), compact)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(day: ScheduleDayUiModel, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier, compact: Boolean) {
    val todayLabel = stringResource(R.string.calendar_today)
    val description = buildList {
        add(day.date.toString())
        if (day.isToday) add(todayLabel)
        day.events.forEach { add(stringResource(R.string.calendar_event, it.sportTitle, it.state.label())) }
    }.joinToString(", ")
    val border = when { isSelected -> MaterialTheme.colorScheme.primary; day.isToday -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) }
    Box(modifier.clip(RoundedCornerShape(10.dp))
        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surface)
        .border(1.dp, border, RoundedCornerShape(10.dp))
        .semantics(mergeDescendants = true) { selected = isSelected; contentDescription = description }
        .clickable(role = Role.Button, onClick = onClick).padding(2.dp)) {
        if (compact) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall,
                    color = if (day.inCurrentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Medium)
                day.events.firstOrNull()?.let { event ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SportBadge(event.sportSlug, size = 9.dp)
                        Icon(event.state.icon(), null, Modifier.size(9.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            Text(day.date.dayOfMonth.toString(), Modifier.align(Alignment.TopCenter),
                style = MaterialTheme.typography.titleSmall,
                color = if (day.inCurrentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Medium)
            Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                day.events.take(2).forEach { event ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SportBadge(event.sportSlug, size = 10.dp)
                        Icon(event.state.icon(), null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (day.events.size > 2) Text("+", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

fun ScheduleEventState.icon() = when (this) {
    ScheduleEventState.COMPLETED -> Icons.Rounded.Check
    ScheduleEventState.PLANNED -> Icons.Rounded.RadioButtonUnchecked
    ScheduleEventState.CANCELED -> Icons.Rounded.Close
}

@Composable
private fun ScheduleEventState.label() = stringResource(when (this) {
    ScheduleEventState.COMPLETED -> R.string.calendar_completed
    ScheduleEventState.PLANNED -> R.string.calendar_planned
    ScheduleEventState.CANCELED -> R.string.calendar_canceled
})
