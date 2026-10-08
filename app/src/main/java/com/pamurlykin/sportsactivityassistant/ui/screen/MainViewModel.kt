package com.pamurlykin.sportsactivityassistant.ui.screen

import android.content.ContentResolver
import android.net.Uri
import com.pamurlykin.sportsactivityassistant.data.backup.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pamurlykin.sportsactivityassistant.data.model.AddPlannedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.ComplexOptionUiModel
import com.pamurlykin.sportsactivityassistant.data.model.DataOperationUiState
import com.pamurlykin.sportsactivityassistant.data.model.SaveSportsCenterInput
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleMonthUiModel
import com.pamurlykin.sportsactivityassistant.data.model.StatisticsOverviewUiModel
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val today = LocalDate.now()
    private val visibleMonth = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(today)
    private val _scheduleState = MutableStateFlow<ScheduleMonthUiModel?>(null)
    private val _dataOperationState = MutableStateFlow(DataOperationUiState())

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    private val _fileBusy = MutableStateFlow(false)
    private val _exportReady = MutableStateFlow(false)
    val importPreview = _importPreview.asStateFlow()
    val fileBusy = _fileBusy.asStateFlow()
    val exportReady = _exportReady.asStateFlow()
    private var pendingImport: ParsedImport? = null
    private var pendingExport: ByteArray? = null
    private var phase = "idle"
    private var previewJob: Job? = null

    val scheduleState: StateFlow<ScheduleMonthUiModel?> = _scheduleState.asStateFlow()
    val dataOperationState: StateFlow<DataOperationUiState> = _dataOperationState.asStateFlow()
    val statisticsState: StateFlow<StatisticsOverviewUiModel> = repository.observeStatisticsOverview().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatisticsOverviewUiModel(totalTrainings = 0, sports = emptyList()),
    )
    val sportsCenters = repository.observeSportsCenters().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        refreshSchedule()
    }

    fun previousMonth() {
        val newMonth = visibleMonth.value.minusMonths(1)
        visibleMonth.value = newMonth
        selectedDate.value = normalizeSelectedDate(selectedDate.value, newMonth)
        refreshSchedule()
    }

    fun nextMonth() {
        val newMonth = visibleMonth.value.plusMonths(1)
        visibleMonth.value = newMonth
        selectedDate.value = normalizeSelectedDate(selectedDate.value, newMonth)
        refreshSchedule()
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        if (YearMonth.from(date) != visibleMonth.value) {
            visibleMonth.value = YearMonth.from(date)
        }
        refreshSchedule()
    }

    fun addPlannedTraining(input: AddPlannedTrainingInput) {
        viewModelScope.launch {
            runCatching { repository.addPlannedTraining(repository.localProfileId(), input) }
                .onFailure { showError(it) }
                .getOrNull() ?: return@launch
            val month = if (YearMonth.from(input.date) != visibleMonth.value) {
                visibleMonth.value = YearMonth.from(input.date)
                visibleMonth.value
            } else {
                visibleMonth.value
            }
            selectedDate.value = input.date
            _scheduleState.value = repository.getScheduleMonth(repository.localProfileId(), month, selectedDate.value, today)
        }
    }

    fun addCompletedTraining(input: AddCompletedTrainingInput) {
        viewModelScope.launch {
            runCatching { repository.addCompletedTraining(repository.localProfileId(), input) }
                .onFailure { showError(it) }
                .getOrNull() ?: return@launch
            visibleMonth.value = YearMonth.from(input.date)
            selectedDate.value = input.date
            refreshSchedule()
            _dataOperationState.value = DataOperationUiState(message = "Тренировка сохранена")
        }
    }

    fun saveSportsCenter(input: SaveSportsCenterInput) {
        viewModelScope.launch {
            runCatching { repository.saveSportsCenter(input) }
                .onSuccess { _dataOperationState.value = DataOperationUiState(message = "Спортивный центр сохранён") }
                .onFailure(::showError)
        }
    }

    suspend fun loadComplexesForSport(sportId: Int): List<ComplexOptionUiModel> {
        return repository.getComplexOptionsForSport(sportId)
    }

    fun trainingsForSport(sportId: Int) = repository.observeTrainingsForSport(sportId)

    fun statisticsForSport(sportId: Int) = repository.observeSportStatistics(sportId)

    private fun beginFile(nextPhase: String): Boolean {
        if (_fileBusy.value) return false
        _fileBusy.value = true
        phase = nextPhase
        _dataOperationState.value = DataOperationUiState(inProgress = true, message = "Работа с файлом…")
        return true
    }

    fun beginImportSelection(): Boolean = beginFile("importSelection")

    fun importSelected(resolver: ContentResolver, uri: Uri?) {
        if (phase != "importSelection") {
            if (uri != null && phase == "idle") showError(IllegalStateException("Выбор файла прерван. Выберите файл ещё раз; база не изменена."))
            return
        }
        if (uri == null) { cancelFile(); return }
        phase = "reading"
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { LocalFiles.read(resolver, uri) }
                val source = repository.prepareImport(bytes)
                pendingImport = source
                _importPreview.value = repository.previewImport(source)
                phase = "preview"
                _dataOperationState.value = DataOperationUiState()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { finishFile(); showError(e) }
        }
    }

    fun changeImportChoices(choices: ImportChoices) {
        if (phase !in setOf("preview", "previewing")) return
        val source = pendingImport ?: return
        previewJob?.cancel()
        phase = "previewing"
        _dataOperationState.value = DataOperationUiState(inProgress = true, message = "Проверка выбора…")
        previewJob = viewModelScope.launch {
            try {
                _importPreview.value = repository.previewImport(source, choices)
                phase = "preview"
                _dataOperationState.value = DataOperationUiState()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { phase = "preview"; showError(e) }
        }
    }

    fun confirmImport() {
        if (phase != "preview") return
        val source = pendingImport ?: return
        val preview = _importPreview.value?.takeIf { it.canApply } ?: return
        phase = "applying"
        _dataOperationState.value = DataOperationUiState(inProgress = true, message = "Применение импорта…")
        viewModelScope.launch {
            try {
                val r = repository.applyImport(source, preview.choices)
                finishFile()
                _dataOperationState.value = DataOperationUiState(message =
                    "Импорт завершён: ${r.importedTrainings} тренировок добавлено, ${r.skippedTrainings} пропущено; " +
                    "${r.importedPlans} планов, ${r.importedRules} серий, ${r.importedCenters} центров, ${r.importedFavorites} избранных. " +
                    if (r.historicalRouteAttempts > 0) "\n${r.historicalRouteAttempts} исторических попыток без сравнимой оценки." else "")
                refreshSchedule()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                phase = "preview"
                _importPreview.value = repository.previewImport(source, preview.choices)
                showError(e)
            }
        }
    }

    fun prepareExport() {
        if (!beginFile("exportPreparing")) return
        viewModelScope.launch {
            try {
                val bytes = repository.createBackup().toByteArray(Charsets.UTF_8)
                require(bytes.size <= ImportFiles.MAX_BYTES) { "Копия превышает поддерживаемый лимит 16 МиБ" }
                pendingExport = bytes
                phase = "exportSelection"
                _exportReady.value = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { cancelFile(); showError(e) }
        }
    }

    fun exportLaunched() { _exportReady.value = false }

    fun exportSelected(resolver: ContentResolver, uri: Uri?) {
        if (uri == null) { if (phase == "exportSelection") cancelFile(); return }
        val bytes = pendingExport
        if (phase != "exportSelection" || bytes == null) {
            showError(IllegalStateException("Операция прервана. Файл может быть пустым; создайте новую копию."))
            return
        }
        phase = "writing"
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { LocalFiles.writeVerified(resolver, uri, bytes) }
                finishFile()
                _dataOperationState.value = DataOperationUiState(message = "Копия сохранена и проверена чтением файла")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { finishFile(); showError(e) }
        }
    }

    fun cancelFile() {
        if (phase == "applying" || phase == "writing") return
        previewJob?.cancel()
        pendingImport = null; pendingExport = null
        _importPreview.value = null; _exportReady.value = false
        _fileBusy.value = false
        phase = "idle"
        _dataOperationState.value = DataOperationUiState()
    }

    private fun finishFile() {
        phase = "finished"
        cancelFile()
    }

    fun reportDataError(error: Throwable) { finishFile(); showError(error) }

    fun clearDataMessage() {
        if (!_dataOperationState.value.inProgress) _dataOperationState.value = DataOperationUiState()
    }

    fun sportTitle(sportId: Int): String {
        return statisticsState.value.sports.firstOrNull { it.id == sportId }?.title ?: "Статистика"
    }

    private fun refreshSchedule() {
        viewModelScope.launch {
            _scheduleState.value = repository.getScheduleMonth(
                userId = repository.localProfileId(),
                month = visibleMonth.value,
                selectedDate = normalizeSelectedDate(selectedDate.value, visibleMonth.value),
                today = today,
            )
        }
    }

    private fun showError(error: Throwable) {
        _dataOperationState.value = DataOperationUiState(
            message = error.message ?: "Не удалось выполнить операцию",
            isError = true,
        )
    }

    private fun normalizeSelectedDate(date: LocalDate, month: YearMonth): LocalDate {
        return if (YearMonth.from(date) == month) {
            date
        } else {
            month.atDay(1)
        }
    }

    companion object {
        fun provideFactory(repository: AppRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(repository) as T
                }
            }
        }
    }
}
