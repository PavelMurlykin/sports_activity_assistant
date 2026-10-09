package com.pamurlykin.sportsactivityassistant.ui.components

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
        it.fullTitle + if (it.isArchived) AppText.get(R.string.data_exchange_arhiv) else ""
    } ?: if (id == null) AppText.get(R.string.statistics_filters_vse_tsentry) else AppText.get(R.string.statistics_filters_tsentr, id)
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (filter.startDate == null) AppText.get(R.string.statistics_filters_vsyo_vremya) else AppText.get(R.string.date_interval, filter.startDate, filter.endDate),
                modifier = Modifier.testTag("active-statistics-period"))
            Text(centerTitle(filter.centerId), modifier = Modifier.testTag("active-statistics-center"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    allTime = filter.startDate == null
                    start = filter.startDate?.toString() ?: today.withDayOfYear(1).toString()
                    end = filter.endDate?.toString() ?: today.toString()
                    centerId = filter.centerId ?: 0L
                    error = null; open = true
                }, modifier = Modifier.testTag("statistics-filters")) { Text(AppText.get(R.string.statistics_filters_filtry)) }
                if (filter != StatisticsFilter()) TextButton(onClick = { onApply(StatisticsFilter()) }) { Text(AppText.get(R.string.statistics_filters_sbrosit)) }
            }
        }
    }
    if (open) AdaptiveAlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = { open = false },
        title = { Text(AppText.get(R.string.statistics_filters_period_i_tsentr)) },
        text = {
            Column(Modifier.dialogVerticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { allTime = false; start = YearMonth.from(today).atDay(1).toString(); end = YearMonth.from(today).atEndOfMonth().toString() }) { Text(AppText.get(R.string.statistics_filters_etot_mesyats)) }
                    TextButton(onClick = { allTime = false; start = today.withDayOfYear(1).toString(); end = today.withMonth(12).withDayOfMonth(31).toString() }) { Text(AppText.get(R.string.statistics_filters_etot_god)) }
                }
                Row(Modifier.toggleable(value = allTime, role = Role.Checkbox, onValueChange = { allTime = it })
                    .testTag("statistics-all-time")) {
                    Checkbox(allTime, onCheckedChange = null)
                    Text(AppText.get(R.string.statistics_filters_vsyo_vremya), Modifier.padding(top = 12.dp))
                }
                if (!allTime) {
                    OutlinedTextField(start, { start = it }, label = { Text(AppText.get(R.string.statistics_filters_s_daty_gggg_mm_dd)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth().testTag("statistics-start"))
                    OutlinedTextField(end, { end = it }, label = { Text(AppText.get(R.string.statistics_filters_po_datu_gggg_mm_dd)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth().testTag("statistics-end"))
                    Text(AppText.get(R.string.statistics_filters_obe_daty_vklyucheny_pustye_mesyatsy), style = MaterialTheme.typography.bodySmall)
                }
                DropdownSelector(AppText.get(R.string.sports_center_dialog_sportivnyy_tsentr), centerTitle(centerId.takeIf { it != 0L }),
                    listOf<SportsCenterUiModel?>(null) + centers,
                    { it?.let { center -> center.fullTitle + if (center.isArchived) AppText.get(R.string.data_exchange_arhiv) else "" } ?: AppText.get(R.string.statistics_filters_vse_tsentry) },
                    { centerId = it?.id ?: 0L }, modifier = Modifier.testTag("statistics-center"))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("statistics-filter-error")) }
            }
        },
        confirmButton = { TextButton(onClick = {
            try {
                fun date(value: String): LocalDate {
                    require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) { AppText.get(R.string.statistics_filters_vvedite_datu_v_formate_gggg_mm_dd) }
                    return try { LocalDate.parse(value) } catch (_: java.time.DateTimeException) {
                        throw IllegalArgumentException(AppText.get(R.string.statistics_filters_nesuschestvuyuschaya_data))
                    }
                }
                val next = StatisticsFilter(if (allTime) null else date(start),
                    if (allTime) null else date(end), centerId.takeIf { it != 0L })
                onApply(next); open = false
            } catch (e: IllegalArgumentException) { error = e.message }
        }, modifier = Modifier.testTag("statistics-apply")) { Text(AppText.get(R.string.statistics_filters_primenit)) } },
        dismissButton = { TextButton(onClick = { open = false }) { Text(AppText.get(R.string.add_completed_training_dialog_otmena)) } },
    )
}
