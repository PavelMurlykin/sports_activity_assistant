package com.pamurlykin.sportsactivityassistant.ui.screen

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
            runCatching { repository.addPlannedTraining(LOCAL_USER_ID, input) }
                .onFailure { showError(it) }
                .getOrNull() ?: return@launch
            val month = if (YearMonth.from(input.date) != visibleMonth.value) {
                visibleMonth.value = YearMonth.from(input.date)
                visibleMonth.value
            } else {
                visibleMonth.value
            }
            selectedDate.value = input.date
            _scheduleState.value = repository.getScheduleMonth(LOCAL_USER_ID, month, selectedDate.value, today)
        }
    }

    fun addCompletedTraining(input: AddCompletedTrainingInput) {
        viewModelScope.launch {
            runCatching { repository.addCompletedTraining(LOCAL_USER_ID, input) }
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

    suspend fun createBackup(): String = repository.createBackup()

    fun importData(bytes: ByteArray) {
        viewModelScope.launch {
            _dataOperationState.value = DataOperationUiState(inProgress = true, message = "Импорт данных…")
            runCatching { repository.importData(bytes, LOCAL_USER_ID) }
                .onSuccess { result ->
                    _dataOperationState.value = DataOperationUiState(
                        message = "Импорт завершён: ${result.importedTrainings} добавлено, ${result.skippedTrainings} пропущено (${result.source})",
                    )
                    refreshSchedule()
                }
                .onFailure(::showError)
        }
    }

    fun reportBackupSaved() {
        _dataOperationState.value = DataOperationUiState(message = "Резервная копия сохранена")
    }

    fun reportDataError(error: Throwable) = showError(error)

    fun clearDataMessage() {
        _dataOperationState.value = DataOperationUiState()
    }

    fun sportTitle(sportId: Int): String {
        return statisticsState.value.sports.firstOrNull { it.id == sportId }?.title ?: "Статистика"
    }

    private fun refreshSchedule() {
        viewModelScope.launch {
            _scheduleState.value = repository.getScheduleMonth(
                userId = LOCAL_USER_ID,
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
        private const val LOCAL_USER_ID = 1L

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
