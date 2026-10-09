package com.pamurlykin.sportsactivityassistant.ui.screen

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import android.content.ContentResolver
import android.net.Uri
import com.pamurlykin.sportsactivityassistant.data.backup.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import com.pamurlykin.sportsactivityassistant.data.model.TrainingEditSnapshot
import kotlinx.coroutines.withContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.pamurlykin.sportsactivityassistant.data.model.StatisticsFilter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: AppRepository,
    private val savedState: SavedStateHandle,
    private val currentDate: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    @androidx.annotation.VisibleForTesting
    constructor(repository: AppRepository, currentDate: () -> LocalDate = { LocalDate.now() }) :
        this(repository, SavedStateHandle(), currentDate)

    private var today = currentDate()
    private val selectedDate = MutableStateFlow(runCatching {
        savedState.get<String>("scheduleDate")?.let(LocalDate::parse)?.takeIf { it.year in 1..9999 }
    }.getOrNull() ?: today)
    private val visibleMonth = MutableStateFlow(YearMonth.from(selectedDate.value))
    private val _scheduleState = MutableStateFlow<ScheduleMonthUiModel?>(null)
    private val _scheduleReadState = MutableStateFlow(ReadState<ScheduleMonthUiModel>())
    val scheduleReadState = _scheduleReadState.asStateFlow()
    private val readRevision = MutableStateFlow(0L)
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
    private var fileJob: Job? = null
    private var scheduleJob: Job? = null
    private val trainingWrites = mutableMapOf<String, Deferred<Unit>>()

    val scheduleState: StateFlow<ScheduleMonthUiModel?> = _scheduleState.asStateFlow()
    val dataOperationState: StateFlow<DataOperationUiState> = _dataOperationState.asStateFlow()
    private val _statisticsFilter = MutableStateFlow(runCatching {
        val values = savedState.get<List<String>>("statisticsFilter") ?: emptyList()
        if (values.size != 3) StatisticsFilter() else StatisticsFilter(
            values[0].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
            values[1].takeIf { it.isNotEmpty() }?.let(LocalDate::parse), values[2].toLongOrNull())
    }.getOrDefault(StatisticsFilter()))
    val statisticsFilter = _statisticsFilter.asStateFlow()

    fun setStatisticsFilter(filter: StatisticsFilter) {
        savedState["statisticsFilter"] = arrayListOf(filter.startDate?.toString().orEmpty(),
            filter.endDate?.toString().orEmpty(), filter.centerId?.toString().orEmpty())
        _statisticsFilter.value = filter
    }

    val statisticsReadState = readRevision.flatMapLatest {
        statisticsFilter.flatMapLatest { filter -> repository.observeStatisticsOverview(filter).readStates() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadState())
    // Keep reference options while a form is open; screen content uses explicit read states.
    val statisticsState: StateFlow<StatisticsOverviewUiModel> = statisticsReadState.mapNotNull {
        it.data
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatisticsOverviewUiModel(totalTrainings = 0, sports = emptyList()),
    )
    val centersReadState = readRevision.flatMapLatest {
        repository.observeSportsCenters().readStates()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadState())
    val sportsCenters = centersReadState.mapNotNull { it.data }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        refreshSchedule()
        viewModelScope.launch {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                kotlinx.coroutines.delay(30_000)
                refreshToday()
            }
        }
    }

    /** Restarts failed collectors; no mutation, import or file operation is retried here. */
    fun retryReads() {
        readRevision.value += 1
        refreshSchedule()
    }

    /** Also called on resume: handles midnight, device date and time-zone changes. */
    fun refreshToday() {
        val next = currentDate()
        if (next == today) return
        if (selectedDate.value == today && visibleMonth.value == YearMonth.from(today)) {
            selectedDate.value = next
            visibleMonth.value = YearMonth.from(next)
            savedState["scheduleDate"] = next.toString()
        }
        today = next
        refreshSchedule()
    }

    fun previousMonth() {
        if (visibleMonth.value == YearMonth.of(1, 1)) return
        val newMonth = visibleMonth.value.minusMonths(1)
        visibleMonth.value = newMonth
        selectedDate.value = normalizeSelectedDate(selectedDate.value, newMonth)
        savedState["scheduleDate"] = selectedDate.value.toString()
        refreshSchedule()
    }

    fun nextMonth() {
        if (visibleMonth.value == YearMonth.of(9999, 12)) return
        val newMonth = visibleMonth.value.plusMonths(1)
        visibleMonth.value = newMonth
        selectedDate.value = normalizeSelectedDate(selectedDate.value, newMonth)
        savedState["scheduleDate"] = selectedDate.value.toString()
        refreshSchedule()
    }

    fun selectDate(date: LocalDate) {
        if (date.year !in 1..9999) return
        selectedDate.value = date
        savedState["scheduleDate"] = date.toString()
        if (YearMonth.from(date) != visibleMonth.value) {
            visibleMonth.value = YearMonth.from(date)
        }
        refreshSchedule()
    }

    suspend fun addPlannedTraining(input: AddPlannedTrainingInput, requestId: String) {
        trainingWrite(requestId) {
            repository.addPlannedTraining(repository.localProfileId(), input, requestId)
            selectDate(input.date)
        }
    }

    suspend fun loadPlan(key: String, scope: com.pamurlykin.sportsactivityassistant.data.model.PlanScope) =
        repository.loadPlan(key, scope)

    suspend fun updatePlan(snapshot: com.pamurlykin.sportsactivityassistant.data.model.PlanSnapshot,
        scope: com.pamurlykin.sportsactivityassistant.data.model.PlanScope, input: AddPlannedTrainingInput, requestId: String) {
        trainingWrite(requestId) { repository.updatePlan(snapshot, scope, input); selectDate(input.date) }
    }

    suspend fun cancelPlan(snapshot: com.pamurlykin.sportsactivityassistant.data.model.PlanSnapshot,
        scope: com.pamurlykin.sportsactivityassistant.data.model.PlanScope, requestId: String) {
        trainingWrite(requestId) { repository.cancelPlan(snapshot, scope) }
    }

    suspend fun completePlan(snapshot: com.pamurlykin.sportsactivityassistant.data.model.PlanSnapshot,
        input: AddCompletedTrainingInput, requestId: String) {
        trainingWrite(requestId) { repository.completePlan(snapshot, input, requestId); selectDate(input.date) }
    }

    /** Runs independently of a dialog's composition; a rotation only detaches its waiter. */
    private suspend fun trainingWrite(key: String, block: suspend () -> Unit) {
        if (trainingWrites.size >= 64) trainingWrites.entries.removeAll { it.value.isCompleted }
        val task = trainingWrites[key] ?: viewModelScope.async {
            try { block() }
            catch (e: Exception) { trainingWrites.remove(key); throw e }
        }.also { trainingWrites[key] = it }
        task.await()
    }

    suspend fun saveCompletedTraining(input: AddCompletedTrainingInput, requestId: String, snapshot: TrainingEditSnapshot? = null) {
        trainingWrite(requestId) {
            if (snapshot == null) repository.addCompletedTraining(repository.localProfileId(), input, requestId)
            else repository.updateCompletedTraining(snapshot, input)
            selectDate(input.date)
        }
    }

    suspend fun loadTrainingForEdit(id: Long) = repository.loadTrainingForEdit(id)

    suspend fun deleteCompletedTraining(snapshot: TrainingEditSnapshot, requestId: String) {
        trainingWrite(requestId) {
            repository.deleteCompletedTraining(snapshot)
            refreshSchedule()
        }
    }

    private val centerWrites = mutableMapOf<String, kotlinx.coroutines.Deferred<Long>>()

    suspend fun saveSportsCenter(input: SaveSportsCenterInput): Long {
        val key = input.requestId ?: return repository.saveSportsCenter(input)
        if (centerWrites.size >= 64) centerWrites.entries.removeAll { it.value.isCompleted }
        val task = centerWrites[key] ?: viewModelScope.async {
            try { repository.saveSportsCenter(input) }
            catch (e: Exception) { centerWrites.remove(key); throw e }
        }.also { centerWrites[key] = it }
        return task.await()
    }

    suspend fun setSportsCenterArchived(id: Long, archived: Boolean, requestId: String = java.util.UUID.randomUUID().toString()) {
        trainingWrite(requestId) {
            repository.setSportsCenterArchived(id, archived)
            refreshSchedule()
        }
    }

    suspend fun loadComplexesForSport(sportId: Int): List<ComplexOptionUiModel> {
        return repository.getComplexOptionsForSport(sportId)
    }

    fun trainingPageReadStates(sportId: Int, page: Int) = readRevision.flatMapLatest {
        statisticsFilter.flatMapLatest { filter -> repository.observeTrainingPage(sportId, filter, page).readStates() }
    }

    fun sportStatisticsReadStates(sportId: Int) = readRevision.flatMapLatest {
        statisticsFilter.flatMapLatest { filter ->
            repository.observeSportStatistics(sportId, filter).map { requireNotNull(it) }.readStates()
        }
    }

    fun trainingsPageForSport(sportId: Int, page: Int) = trainingPageReadStates(sportId, page).map { it.data }
    fun statisticsForSport(sportId: Int) = sportStatisticsReadStates(sportId).map { it.data }

    private fun beginFile(nextPhase: String): Boolean {
        if (_fileBusy.value) return false
        _fileBusy.value = true
        phase = nextPhase
        _dataOperationState.value = DataOperationUiState(inProgress = true, message = AppText.get(R.string.main_view_model_rabota_s_faylom))
        return true
    }

    fun beginImportSelection(): Boolean = beginFile("importSelection")

    fun importSelected(resolver: ContentResolver, uri: Uri?) {
        if (phase != "importSelection") {
            if (uri != null && phase == "idle") showError(IllegalStateException(AppText.get(R.string.main_view_model_vybor_fayla_prervan_vyberite_fayl)))
            return
        }
        if (uri == null) { cancelFile(); return }
        phase = "reading"
        fileJob = viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { LocalFiles.read(resolver, uri) }
                val source = repository.prepareImport(bytes)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (phase != "reading") return@launch
                pendingImport = source
                val next = repository.previewImport(source)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (phase != "reading") return@launch
                _importPreview.value = next
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
        _dataOperationState.value = DataOperationUiState(inProgress = true, message = AppText.get(R.string.main_view_model_proverka_vybora))
        previewJob = viewModelScope.launch {
            try {
                val next = repository.previewImport(source, choices)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (pendingImport !== source || phase != "previewing") return@launch
                _importPreview.value = next
                phase = "preview"
                _dataOperationState.value = DataOperationUiState()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (pendingImport === source && phase == "previewing") { phase = "preview"; showError(e) }
            }
        }
    }

    fun confirmImport() {
        if (phase != "preview") return
        val source = pendingImport ?: return
        val preview = _importPreview.value?.takeIf { it.canApply } ?: return
        phase = "applying"
        _dataOperationState.value = DataOperationUiState(inProgress = true, message = AppText.get(R.string.main_view_model_primenenie_importa))
        fileJob = viewModelScope.launch {
            try {
                val r = repository.applyImport(source, preview.choices)
                finishFile()
                _dataOperationState.value = DataOperationUiState(message =
                    AppText.get(R.string.main_view_model_import_zavershyon_trenirovok_dobavleno_propuscheno, r.importedTrainings, r.skippedTrainings) +
                    AppText.get(R.string.main_view_model_planov_seriy_tsentrov_izbrannyh, r.importedPlans, r.importedRules, r.importedCenters, r.importedFavorites) +
                    if (r.historicalRouteAttempts > 0) AppText.get(R.string.main_view_model_n_istoricheskih_popytok_bez_sravnimoy, r.historicalRouteAttempts) else "")
                refreshSchedule()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                phase = "preview"
                try { _importPreview.value = repository.previewImport(source, preview.choices) }
                catch (canceled: CancellationException) { throw canceled }
                catch (_: Exception) { finishFile() }
                showError(e)
            }
        }
    }

    fun prepareExport() {
        if (!beginFile("exportPreparing")) return
        fileJob = viewModelScope.launch {
            try {
                val bytes = repository.createBackup().toByteArray(Charsets.UTF_8)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (phase != "exportPreparing") return@launch
                require(bytes.size <= ImportFiles.MAX_BYTES) { AppText.get(R.string.app_repository_kopiya_prevyshaet_podderzhivaemyy_limit_16) }
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
            showError(IllegalStateException(AppText.get(R.string.main_view_model_operatsiya_prervana_fayl_mozhet_byt)))
            return
        }
        phase = "writing"
        fileJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { LocalFiles.writeVerified(resolver, uri, bytes) }
                finishFile()
                _dataOperationState.value = DataOperationUiState(message = AppText.get(R.string.main_view_model_kopiya_sohranena_i_proverena_chteniem))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { finishFile(); showError(e) }
        }
    }

    fun cancelFile() {
        if (phase == "applying" || phase == "writing") return
        previewJob?.cancel()
        fileJob?.cancel()
        fileJob = null
        pendingImport = null; pendingExport = null
        _importPreview.value = null; _exportReady.value = false
        _fileBusy.value = false
        phase = "idle"
        _dataOperationState.value = DataOperationUiState()
    }

    private fun finishFile() {
        phase = "finished"
        fileJob = null
        cancelFile()
    }

    fun reportDataError(error: Throwable) { finishFile(); showError(error) }

    fun clearDataMessage() {
        if (!_dataOperationState.value.inProgress) _dataOperationState.value = DataOperationUiState()
    }

    fun sportTitle(sportId: Int): String {
        return statisticsState.value.sports.firstOrNull { it.id == sportId }?.title ?: AppText.get(R.string.app_repository_statistika)
    }

    private fun refreshSchedule() {
        scheduleJob?.cancel()
        val month = visibleMonth.value
        val date = normalizeSelectedDate(selectedDate.value, month)
        if (_scheduleState.value?.month != month || _scheduleState.value?.selectedDate != date) _scheduleState.value = null
        _scheduleReadState.value = ReadState(data = _scheduleState.value)
        scheduleJob = viewModelScope.launch {
            try {
                repository.observeScheduleMonth(repository.localProfileId(), month, date, today).collect { next ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (month == visibleMonth.value && date == selectedDate.value) {
                        _scheduleState.value = next
                        _scheduleReadState.value = ReadState(data = next)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (month == visibleMonth.value && date == selectedDate.value) {
                    _scheduleState.value = null
                    _scheduleReadState.value = ReadState(failed = true)
                }
            }
        }
    }

    private fun showError(error: Throwable) {
        _dataOperationState.value = DataOperationUiState(
            message = error.message ?: AppText.get(R.string.main_view_model_ne_udalos_vypolnit_operatsiyu),
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
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return MainViewModel(repository, extras.createSavedStateHandle()) as T
                }
            }
        }
    }
}
