package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun StatisticsFilters(
    filter: StatisticsFilter,
    centers: List<SportsCenterUiModel>,
    onApply: (StatisticsFilter) -> Unit,
    today: LocalDate = LocalDate.now(),
    sportId: Int? = null,
) {
    var calendar by rememberSaveable { mutableIntStateOf(0) }
    var choosingCenters by rememberSaveable { mutableStateOf(false) }
    val available = centers.filter { sportId == null || it.sports.any { sport -> sport.id == sportId } }
    val month = YearMonth.from(today)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter.startDate == null && filter.endDate == null,
                        onClick = { onApply(filter.copy(startDate = null, endDate = null)) },
                        label = { Text(stringResource(R.string.statistics_filters_vsyo_vremya)) })
                    FilterChip(selected = filter.startDate == month.atDay(1) && filter.endDate == month.atEndOfMonth(),
                        onClick = { onApply(filter.copy(startDate = month.atDay(1), endDate = month.atEndOfMonth())) },
                        label = { Text(stringResource(R.string.statistics_period_month)) })
                    FilterChip(selected = filter.startDate == today.withDayOfYear(1) && filter.endDate == today.withMonth(12).withDayOfMonth(31),
                        onClick = { onApply(filter.copy(startDate = today.withDayOfYear(1), endDate = today.withMonth(12).withDayOfMonth(31))) },
                        label = { Text(stringResource(R.string.statistics_period_year)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatisticsDateBlock(stringResource(R.string.statistics_date_from), filter.startDate,
                        Modifier.weight(1f).testTag("statistics-start")) { calendar = 1 }
                    StatisticsDateBlock(stringResource(R.string.statistics_date_to), filter.endDate,
                        Modifier.weight(1f).testTag("statistics-end")) { calendar = 2 }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onApply(filter.copy(centerIds = emptySet())) },
                    modifier = Modifier.weight(1f).testTag("statistics-all-centers").semantics { selected = filter.centerIds.isEmpty() },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (filter.centerIds.isEmpty())
                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                    Text(stringResource(R.string.statistics_filters_vse_tsentry))
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { choosingCenters = true }, modifier = Modifier.fillMaxWidth().testTag("statistics-choose-centers")) {
                        Text(stringResource(R.string.statistics_centers_choose))
                        if (filter.centerIds.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Badge { Text(filter.centerIds.size.toString()) }
                        }
                    }
                    DropdownMenu(expanded = choosingCenters, onDismissRequest = { choosingCenters = false },
                        modifier = Modifier.heightIn(max = 320.dp).testTag("statistics-centers-list")) {
                        if (available.isEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.statistics_no_centers)) },
                            onClick = {}, enabled = false)
                        available.forEach { center ->
                            val checked = center.id in filter.centerIds
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Checkbox(checked, onCheckedChange = null)
                                    Text(center.fullTitle + if (center.isArchived) stringResource(R.string.data_exchange_arhiv) else "")
                                }
                            }, modifier = Modifier.testTag("statistics-center-" + center.id).semantics { selected = checked },
                                onClick = {
                                    val eligible = filter.centerIds.intersect(available.map { it.id }.toSet())
                                    onApply(filter.copy(centerIds = if (checked) eligible - center.id else eligible + center.id))
                                })
                        }
                    }
                }
            }
        }
    }
    if (calendar != 0) {
        val start = calendar == 1
        val initial = if (start) filter.startDate ?: minOf(today, filter.endDate ?: today)
            else filter.endDate ?: maxOf(today, filter.startDate ?: today)
        TrainingDatePickerDialog(initial, { calendar = 0 }, { day ->
            onApply(if (start) filter.copy(startDate = day) else filter.copy(endDate = day))
            calendar = 0
        }, earliestDate = if (start) null else filter.startDate, latestDate = if (start) filter.endDate else null)
    }
}

@Composable
private fun StatisticsDateBlock(label: String, date: LocalDate?, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(role = Role.Button, onClick = onClick), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date?.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")) ?: stringResource(R.string.date_unlimited),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
