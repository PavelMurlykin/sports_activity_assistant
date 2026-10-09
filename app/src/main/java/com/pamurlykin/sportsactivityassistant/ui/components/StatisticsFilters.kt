package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StatisticsFilters(
    filter: StatisticsFilter,
    centers: List<SportsCenterUiModel>,
    onApply: (StatisticsFilter) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var allTime by rememberSaveable { mutableStateOf(filter.startDate == null) }
    var start by rememberSaveable { mutableStateOf(filter.startDate?.toString() ?: today.withDayOfYear(1).toString()) }
    var end by rememberSaveable { mutableStateOf(filter.endDate?.toString() ?: today.toString()) }
    var centerId by rememberSaveable { mutableLongStateOf(filter.centerId ?: 0L) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun centerTitle(id: Long?) = centers.firstOrNull { it.id == id }?.let {
        it.fullTitle + if (it.isArchived) " · архив" else ""
    } ?: if (id == null) "Все центры" else "Центр #$id"
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (filter.startDate == null) "Всё время" else "${filter.startDate} — ${filter.endDate}",
                modifier = Modifier.testTag("active-statistics-period"))
            Text(centerTitle(filter.centerId), modifier = Modifier.testTag("active-statistics-center"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    allTime = filter.startDate == null
                    start = filter.startDate?.toString() ?: today.withDayOfYear(1).toString()
                    end = filter.endDate?.toString() ?: today.toString()
                    centerId = filter.centerId ?: 0L
                    error = null; open = true
                }, modifier = Modifier.testTag("statistics-filters")) { Text("Фильтры") }
                if (filter != StatisticsFilter()) TextButton(onClick = { onApply(StatisticsFilter()) }) { Text("Сбросить") }
            }
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Период и центр") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { allTime = false; start = YearMonth.from(today).atDay(1).toString(); end = YearMonth.from(today).atEndOfMonth().toString() }) { Text("Этот месяц") }
                    TextButton(onClick = { allTime = false; start = today.withDayOfYear(1).toString(); end = today.withMonth(12).withDayOfMonth(31).toString() }) { Text("Этот год") }
                }
                Row {
                    Checkbox(allTime, onCheckedChange = { allTime = it }, modifier = Modifier.testTag("statistics-all-time"))
                    Text("Всё время", Modifier.padding(top = 12.dp))
                }
                if (!allTime) {
                    OutlinedTextField(start, { start = it }, label = { Text("С даты · ГГГГ-ММ-ДД") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth().testTag("statistics-start"))
                    OutlinedTextField(end, { end = it }, label = { Text("По дату · ГГГГ-ММ-ДД") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth().testTag("statistics-end"))
                    Text("Обе даты включены. Пустые месяцы показываются с нулём.", style = MaterialTheme.typography.bodySmall)
                }
                DropdownSelector("Спортивный центр", centerTitle(centerId.takeIf { it != 0L }),
                    listOf<SportsCenterUiModel?>(null) + centers,
                    { it?.let { center -> center.fullTitle + if (center.isArchived) " · архив" else "" } ?: "Все центры" },
                    { centerId = it?.id ?: 0L }, modifier = Modifier.testTag("statistics-center"))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("statistics-filter-error")) }
            }
        },
        confirmButton = { TextButton(onClick = {
            try {
                fun date(value: String): LocalDate {
                    require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) { "Введите дату в формате ГГГГ-ММ-ДД" }
                    return try { LocalDate.parse(value) } catch (_: java.time.DateTimeException) {
                        throw IllegalArgumentException("Несуществующая дата")
                    }
                }
                val next = StatisticsFilter(if (allTime) null else date(start),
                    if (allTime) null else date(end), centerId.takeIf { it != 0L })
                onApply(next); open = false
            } catch (e: IllegalArgumentException) { error = e.message }
        }, modifier = Modifier.testTag("statistics-apply")) { Text("Применить") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Отмена") } },
    )
}
