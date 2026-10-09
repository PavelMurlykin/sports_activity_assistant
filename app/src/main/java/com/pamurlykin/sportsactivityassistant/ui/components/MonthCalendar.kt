package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
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
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarNavigationButton(onClick = onPreviousMonth, icon = Icons.Rounded.ChevronLeft, label = "Предыдущий месяц")
                Text(
                    text = state.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru")).replaceFirstChar { it.uppercase() } + " " + state.month.year,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Pine,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                CalendarNavigationButton(onClick = onNextMonth, icon = Icons.Rounded.ChevronRight, label = "Следующий месяц")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { dayName ->
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = Pine.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Text("✓ Состоялась · ○ План · × Отменена", style = MaterialTheme.typography.labelSmall)
            state.days.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    week.forEach { day ->
                        CalendarDayCell(
                            day = day,
                            isSelected = day.date == state.selectedDate,
                            onClick = { onDaySelected(day.date) },
                            modifier = Modifier.weight(1f),
                        )
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
    val borderColor = when {
        isSelected -> Pine
        day.isToday -> Clay
        else -> Pine.copy(alpha = 0.08f)
    }

    Column(
        modifier = modifier
            .height(86.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Pine.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .semantics(mergeDescendants = true) {
                selected = isSelected
                contentDescription = buildString {
                    append(day.date.toString())
                    if (day.isToday) append(", сегодня")
                    day.events.forEach { append(", ${it.sportTitle}: ${it.state.label()}") }
                }
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                    Text(when (event.state) {
                        ScheduleEventState.COMPLETED -> "✓"
                        ScheduleEventState.PLANNED -> "○"
                        ScheduleEventState.CANCELED -> "×"
                    }, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (day.events.size > 2) {
                Text(
                    text = "+${day.events.size - 2}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Pine,
                )
            }
        }
    }
}

private fun ScheduleEventState.label() = when (this) {
    ScheduleEventState.COMPLETED -> "состоялась"
    ScheduleEventState.PLANNED -> "запланирована"
    ScheduleEventState.CANCELED -> "отменена"
}
