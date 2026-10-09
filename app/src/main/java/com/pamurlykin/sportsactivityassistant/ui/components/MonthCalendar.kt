package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleDayUiModel
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleEventState
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleMonthUiModel
import com.pamurlykin.sportsactivityassistant.ui.theme.Clay
import com.pamurlykin.sportsactivityassistant.ui.theme.Mist
import com.pamurlykin.sportsactivityassistant.ui.theme.Pine
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.platform.LocalDensity
import com.pamurlykin.sportsactivityassistant.R

@Composable
fun MonthCalendar(
    state: ScheduleMonthUiModel,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (java.time.LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Mist),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarNavigationButton(onClick = onPreviousMonth, icon = Icons.Rounded.ChevronLeft, label = stringResource(R.string.previous_month))
                Text(
                    text = state.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru")).replaceFirstChar { it.uppercase() } + " " + state.month.year,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Pine,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                CalendarNavigationButton(onClick = onNextMonth, icon = Icons.Rounded.ChevronRight, label = stringResource(R.string.next_month))
            }

            Text(stringResource(R.string.calendar_legend), style = MaterialTheme.typography.labelSmall)
            // Seven non-overlapping 48 dp targets even on compact screens. Only the grid scrolls.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gridWidth = maxWidth.coerceAtLeast(348.dp)
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(gridWidth), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            stringArrayResource(R.array.calendar_weekdays).forEach { dayName ->
                                Text(dayName, style = MaterialTheme.typography.labelLarge,
                                    color = Pine.copy(alpha = 0.72f), textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                        state.days.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                week.forEach { day ->
                                    CalendarDayCell(day, day.date == state.selectedDate,
                                        { onDaySelected(day.date) }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarNavigationButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Pine)
    }
}

@Composable
private fun CalendarDayCell(
    day: ScheduleDayUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayLabel = stringResource(R.string.calendar_today)
    val description = buildList {
        add(day.date.toString())
        if (day.isToday) add(todayLabel)
        day.events.forEach { add(stringResource(R.string.calendar_event, it.sportTitle, it.state.label())) }
    }.joinToString(", ")
    val minimumHeight = (86 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    val borderColor = when {
        isSelected -> Pine
        day.isToday -> Clay
        else -> Pine.copy(alpha = 0.08f)
    }

    Column(
        modifier = modifier
            .heightIn(min = minimumHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Pine.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .semantics(mergeDescendants = true) {
                selected = isSelected
                contentDescription = description
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = if (day.inCurrentMonth) Pine else Pine.copy(alpha = 0.35f),
            fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            day.events.take(2).forEach { event ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (event.state == ScheduleEventState.COMPLETED) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f)
                            } else {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(when (event.state) {
                        ScheduleEventState.COMPLETED -> Icons.Rounded.Check
                        ScheduleEventState.PLANNED -> Icons.Rounded.RadioButtonUnchecked
                        ScheduleEventState.CANCELED -> Icons.Rounded.Close
                    }, contentDescription = null, modifier = Modifier.size(12.dp))
                }
            }
            if (day.events.size > 2) {
                Text(
                    text = stringResource(R.string.calendar_more_events, day.events.size - 2),
                    style = MaterialTheme.typography.labelSmall,
                    color = Pine,
                )
            }
        }
    }
}

@Composable
private fun ScheduleEventState.label() = stringResource(when (this) {
    ScheduleEventState.COMPLETED -> R.string.calendar_completed
    ScheduleEventState.PLANNED -> R.string.calendar_planned
    ScheduleEventState.CANCELED -> R.string.calendar_canceled
})
