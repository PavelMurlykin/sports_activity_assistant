package com.pamurlykin.sportsactivityassistant.ui.screen

import com.pamurlykin.sportsactivityassistant.text.AppText
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextAlign
import com.pamurlykin.sportsactivityassistant.ui.components.StatisticsFilters
import com.pamurlykin.sportsactivityassistant.ui.components.FootballStatisticsCards
import com.pamurlykin.sportsactivityassistant.ui.components.CompletedTrainingCard
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import java.time.format.DateTimeFormatter
import com.pamurlykin.sportsactivityassistant.data.model.TrainingPageUiModel
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableLongStateOf
import com.pamurlykin.sportsactivityassistant.ui.components.TrainingActionDialog
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import android.content.res.Configuration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.TextStyle
import java.util.Locale
import com.pamurlykin.sportsactivityassistant.ui.components.SportBadge
import com.pamurlykin.sportsactivityassistant.ui.components.ReadStateNotice
import androidx.compose.ui.res.stringResource
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.StatisticsOverviewUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: MainViewModel,
    onOpenSport: (Int) -> Unit,
    onAddTraining: (Int) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val readState by viewModel.statisticsReadState.collectAsStateWithLifecycle()
    val statisticsState = readState.data ?: StatisticsOverviewUiModel(0, emptyList())
    val filter by viewModel.statisticsFilter.collectAsStateWithLifecycle()
    val centersRead by viewModel.centersReadState.collectAsStateWithLifecycle()
    val centers = centersRead.data.orEmpty()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_statistics)) },
                actions = { com.pamurlykin.sportsactivityassistant.ui.components.SettingsAction(onOpenSettings) },
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().testTag("statistics-overview"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = innerPadding.calculateTopPadding() + 12.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { StatisticsFilters(filter, centers, viewModel::setStatisticsFilter) }
            if (readState.failed || centersRead.failed) {
                item(span = { GridItemSpan(maxLineSpan) }) { ReadStateNotice(true, viewModel::retryReads) }
            } else if (readState.loading || centersRead.loading || statisticsState.filter != filter) {
                item(span = { GridItemSpan(maxLineSpan) }) { ReadStateNotice(false, viewModel::retryReads) }
            } else {
                if (statisticsState.sports.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_choose_sports)) }
                }
                gridItems(statisticsState.sports, key = { "sport-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { sport ->
                    val openLabel = stringResource(R.string.open_sport, sport.title)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().testTag("statistics-sport-${sport.slug}")
                            .clickable(role = Role.Button, onClickLabel = openLabel) { onOpenSport(sport.id) },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SportBadge(slug = sport.slug)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = sport.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = AppText.get(R.string.statistics_screen_trenirovok, sport.completedTrainings),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onAddTraining(sport.id) }) {
                                    Icon(Icons.Rounded.Add, stringResource(R.string.add_sport_training, sport.title))
                                }
                            }
                            StatisticsDateRow(Icons.Rounded.EventAvailable, stringResource(R.string.statistics_last_training), sport.lastTrainingDate,
                                Modifier.testTag("sport-last-${sport.id}"))
                            StatisticsDateRow(Icons.Rounded.CalendarMonth, stringResource(R.string.statistics_next_planned), sport.nextPlannedDate,
                                Modifier.testTag("sport-next-${sport.id}"))
                        }
                    }
                }

                if (statisticsState.months.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(AppText.get(R.string.statistics_screen_po_mesyatsam), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    gridItems(statisticsState.months, key = { "month-${it.month}" }) { month ->
                        Card(Modifier.fillMaxWidth().testTag("statistics-month-${month.month}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
                                Text(AppText.get(R.string.statistics_screen_vsego, month.totalTrainings))
                                if (month.countsBySport.isEmpty()) Text(AppText.get(R.string.statistics_screen_net_trenirovok),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else month.countsBySport.forEach { count -> Text(count, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsDateRow(icon: ImageVector, label: String, date: java.time.LocalDate?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(date?.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")) ?: "—", style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrainingPageControls(page: TrainingPageUiModel, onPage: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("statistics-pages"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = { onPage(page.page - 1) }, enabled = page.page > 0) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.statistics_screen_predyduschaya))
        }
        Text(AppText.get(R.string.paging_index, page.page + 1, page.pageCount), Modifier.padding(horizontal = 12.dp))
        IconButton(onClick = { onPage(page.page + 1) }, enabled = page.page + 1 < page.pageCount) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.statistics_screen_sleduyuschaya))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsDetailScreen(
    sportId: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onAddTraining: () -> Unit = {},
    onEditTraining: (Long) -> Unit = {},
) {
    val filter by viewModel.statisticsFilter.collectAsStateWithLifecycle()
    val centersRead by viewModel.centersReadState.collectAsStateWithLifecycle()
    val centers = centersRead.data.orEmpty()
    var pageIndex by rememberSaveable(sportId, filter) { mutableIntStateOf(0) }
    val pageRead by remember(viewModel, sportId, pageIndex) { viewModel.trainingPageReadStates(sportId, pageIndex) }.collectAsStateWithLifecycle(initialValue = ReadState())
    val trainingPage = pageRead.data
    LaunchedEffect(trainingPage?.page) { trainingPage?.let { pageIndex = it.page } }
    val trainings = trainingPage?.takeIf { it.filter == filter }?.items.orEmpty()
    val statisticsRead by remember(viewModel, sportId) { viewModel.sportStatisticsReadStates(sportId) }.collectAsStateWithLifecycle(initialValue = ReadState())
    val sportStatistics = statisticsRead.data
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
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back_to_statistics))
                    }
                },
                actions = { IconButton(onClick = onAddTraining) { Icon(Icons.Rounded.Add, stringResource(R.string.add_sport_training, title)) } },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = title)
                        if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE) Text(
                            text = AppText.get(R.string.statistics_screen_spisok_trenirovok),
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
            modifier = Modifier.fillMaxSize().testTag("statistics-history"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = innerPadding.calculateTopPadding() + 12.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatisticsFilters(filter, centers, viewModel::setStatisticsFilter, sportId = sportId) }
            if (statisticsRead.failed || centersRead.failed) {
                item { ReadStateNotice(true, viewModel::retryReads) }
            } else if (statisticsRead.loading || centersRead.loading) {
                item { ReadStateNotice(false, viewModel::retryReads) }
            }
            sportStatistics?.takeIf { it.filter == filter }?.let { statistics ->
                item {
                    if (statistics.football != null) FootballStatisticsCards(statistics.football)
                    else if (statistics.climbing != null) com.pamurlykin.sportsactivityassistant.ui.components.ClimbingStatisticsCards(statistics.climbing)
                    else Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(AppText.get(R.string.statistics_screen_itogi), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                    Text(AppText.get(R.string.climbing_module_trenirovki), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            if (pageRead.failed) {
                item { ReadStateNotice(true, viewModel::retryReads) }
            } else if (trainingPage == null || trainingPage.filter != filter) {
                item { ReadStateNotice(false, viewModel::retryReads) }
            } else if (trainings.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = AppText.get(R.string.statistics_screen_poka_pusto), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = AppText.get(R.string.statistics_screen_v_vybrannom_periode_i_tsentre))
                        }
                    }
                }
            } else {
                items(trainings, key = { it.id }) { item ->
                    CompletedTrainingCard(title = item.date.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")),
                        sportSlug = item.sportSlug, center = item.complexTitle, details = item.details, football = item.football,
                        climbing = item.climbing,
                        modifier = Modifier.testTag("training-card-" + item.id),
                        onEdit = { onEditTraining(item.id) },
                        onDelete = { deleting = true; actionId = item.id })
                }
                item { TrainingPageControls(trainingPage, { scrollToTrainings = true; pageIndex = it }) }
            }
        }
    }
}
