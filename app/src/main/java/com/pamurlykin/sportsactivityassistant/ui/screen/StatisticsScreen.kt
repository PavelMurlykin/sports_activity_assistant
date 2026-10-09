package com.pamurlykin.sportsactivityassistant.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextAlign
import com.pamurlykin.sportsactivityassistant.ui.components.StatisticsFilters
import com.pamurlykin.sportsactivityassistant.data.model.TrainingPageUiModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableLongStateOf
import com.pamurlykin.sportsactivityassistant.ui.components.TrainingActions
import com.pamurlykin.sportsactivityassistant.ui.components.TrainingActionDialog
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.TextStyle
import java.util.Locale
import com.pamurlykin.sportsactivityassistant.data.model.TrainingSessionUiModel
import com.pamurlykin.sportsactivityassistant.ui.components.SportBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: MainViewModel,
    onOpenSport: (Int) -> Unit,
) {
    val statisticsState by viewModel.statisticsState.collectAsState()
    val filter by viewModel.statisticsFilter.collectAsState()
    val centers by viewModel.sportsCenters.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Статистика") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = innerPadding.calculateTopPadding() + 12.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StatisticsFilters(filter, centers, viewModel::setStatisticsFilter) }
            if (statisticsState.filter != filter || statisticsState.sports.isEmpty()) {
                item { CircularProgressIndicator() }
            } else {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Всего завершённых тренировок",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = statisticsState.totalTrainings.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                items(statisticsState.sports, key = { it.id }) { sport ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable { onOpenSport(sport.id) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SportBadge(slug = sport.slug)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = sport.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${sport.completedTrainings} тренировок",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                sport.highlights.forEach { highlight ->
                                    Text(
                                        text = highlight,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(text = "Открыть", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (statisticsState.months.isNotEmpty()) {
                    item {
                        Text("По месяцам", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    items(statisticsState.months, key = { it.month.toString() }) { month ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = month.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru"))
                                        .replaceFirstChar { it.uppercase() } + " ${month.month.year}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text("Всего: ${month.totalTrainings}")
                                Text(month.countsBySport.joinToString(" · ").ifEmpty { "Нет тренировок" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsDetailScreen(
    sportId: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val filter by viewModel.statisticsFilter.collectAsState()
    val centers by viewModel.sportsCenters.collectAsState()
    var pageIndex by rememberSaveable(sportId, filter) { mutableIntStateOf(0) }
    val trainingPage by remember(viewModel, sportId, pageIndex) { viewModel.trainingsPageForSport(sportId, pageIndex) }.collectAsState(initial = null)
    LaunchedEffect(trainingPage?.page) { trainingPage?.let { pageIndex = it.page } }
    val trainings = trainingPage?.takeIf { it.filter == filter }?.items.orEmpty()
    val sportStatistics by remember(viewModel, sportId) { viewModel.statisticsForSport(sportId) }.collectAsState(initial = null)
    val listState = rememberLazyListState()
    var scrollToTrainings by remember { mutableStateOf(false) }
    var previousFilter by remember { mutableStateOf(filter) }
    LaunchedEffect(filter) {
        if (previousFilter != filter) listState.scrollToItem(0)
        previousFilter = filter
    }
    LaunchedEffect(trainingPage?.page, trainingPage?.filter, pageIndex, scrollToTrainings) {
        val current = trainingPage
        if (scrollToTrainings && current != null && current.filter == filter && current.page == pageIndex) {
            listState.scrollToItem(3); scrollToTrainings = false
        }
    }
    val title = sportStatistics?.sportTitle ?: viewModel.sportTitle(sportId)
    var actionId by rememberSaveable { mutableLongStateOf(0) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    if (actionId != 0L) TrainingActionDialog(actionId, deleting, viewModel) { actionId = 0 }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад к общей статистике")
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = title)
                        Text(
                            text = "Список тренировок",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = innerPadding.calculateTopPadding() + 12.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatisticsFilters(filter, centers, viewModel::setStatisticsFilter) }
            sportStatistics?.takeIf { it.filter == filter }?.let { statistics ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Итоги", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            statistics.metrics.forEachIndexed { index, metric ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(metric.label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(metric.value, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                                }
                                if (index < statistics.metrics.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
                item {
                    Text("Тренировки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            if (trainingPage == null || trainingPage?.filter != filter) {
                item { CircularProgressIndicator() }
            } else if (trainings.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "Пока пусто", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = "В выбранном периоде и центре нет сохранённых тренировок этого вида спорта. Можно изменить или сбросить фильтры.")
                        }
                    }
                }
            } else {
                item { TrainingPageControls(trainingPage!!, { scrollToTrainings = true; pageIndex = it }) }
                items(trainings, key = { it.id }) { item ->
                    ExpandableTrainingCard(item = item,
                        onEdit = { deleting = false; actionId = item.id },
                        onDelete = { deleting = true; actionId = item.id })
                }
                item { TrainingPageControls(trainingPage!!, { pageIndex = it }) }
            }
        }
    }
}

@Composable
private fun TrainingPageControls(page: TrainingPageUiModel, onPage: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Показано ${page.page * TrainingPageUiModel.SIZE + 1}–${page.page * TrainingPageUiModel.SIZE + page.items.size} из ${page.total}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onPage(page.page - 1) }, enabled = page.page > 0) { Text("Предыдущая") }
            Text("${page.page + 1} / ${page.pageCount}")
            TextButton(onClick = { onPage(page.page + 1) }, enabled = page.page + 1 < page.pageCount) { Text("Следующая") }
        }
    }
}

@Composable
private fun ExpandableTrainingCard(item: TrainingSessionUiModel, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SportBadge(slug = item.sportSlug)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.date.dayOfMonth}.${item.date.monthValue}.${item.date.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = item.complexTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                )
            }

            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrainingActions(item.sportSlug, onEdit, onDelete)
                    item.details.forEach { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
