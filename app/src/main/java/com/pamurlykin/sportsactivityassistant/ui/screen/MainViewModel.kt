package com.pamurlykin.sportsactivityassistant.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pamurlykin.sportsactivityassistant.data.model.AddPlannedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.ComplexOptionUiModel
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

    val scheduleState: StateFlow<ScheduleMonthUiModel?> = _scheduleState.asStateFlow()
    val statisticsState: StateFlow<StatisticsOverviewUiModel> = repository.observeStatisticsOverview().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatisticsOverviewUiModel(totalTrainings = 0, sports = emptyList()),
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
            repository.addPlannedTraining(DEMO_USER_ID, input)
            val month = if (YearMonth.from(input.date) != visibleMonth.value) {
                visibleMonth.value = YearMonth.from(input.date)
                visibleMonth.value
            } else {
                visibleMonth.value
            }
            selectedDate.value = input.date
            _scheduleState.value = repository.getScheduleMonth(DEMO_USER_ID, month, selectedDate.value, today)
        }
    }

    suspend fun loadComplexesForSport(sportId: Int): List<ComplexOptionUiModel> {
        return repository.getComplexOptionsForSport(sportId)
    }

    fun trainingsForSport(sportId: Int) = repository.observeTrainingsForSport(sportId)

    fun sportTitle(sportId: Int): String {
        return statisticsState.value.sports.firstOrNull { it.id == sportId }?.title ?: "Статистика"
    }

    private fun refreshSchedule() {
        viewModelScope.launch {
            _scheduleState.value = repository.getScheduleMonth(
                userId = DEMO_USER_ID,
                month = visibleMonth.value,
                selectedDate = normalizeSelectedDate(selectedDate.value, visibleMonth.value),
                today = today,
            )
        }
    }

    private fun normalizeSelectedDate(date: LocalDate, month: YearMonth): LocalDate {
        return if (YearMonth.from(date) == month) {
            date
        } else {
            month.atDay(1)
        }
    }

    companion object {
        private const val DEMO_USER_ID = 1L

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
