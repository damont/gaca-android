package dev.monts.gaca.ui.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.Briefing
import dev.monts.gaca.data.repo.BriefingRepository
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DailyUiState(
    val date: LocalDate = LocalDate.now(),
    val briefing: Briefing? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) {
    val isToday: Boolean get() = date == LocalDate.now()
}

/** The Today tab: shows one day's briefing from `GET /briefings?date=`. Everything shown
 * comes from the API — this only picks the day and fetches. */
class DailyViewModel(private val repo: BriefingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyUiState())
    val uiState: StateFlow<DailyUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(showSpinner = true)
    }

    fun previousDay() = goTo(_uiState.value.date.minusDays(1))
    fun nextDay() = goTo(_uiState.value.date.plusDays(1))
    fun today() = goTo(LocalDate.now())
    fun refresh() = load(showSpinner = false, isPullToRefresh = true)

    private fun goTo(date: LocalDate) {
        _uiState.update { it.copy(date = date, briefing = null, error = null) }
        load(showSpinner = true)
    }

    private fun load(showSpinner: Boolean, isPullToRefresh: Boolean = false) {
        // Cancel any in-flight load: tapping through days quickly must never let a slow
        // response for an earlier day overwrite the day now on screen.
        loadJob?.cancel()
        val date = _uiState.value.date
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = showSpinner, isRefreshing = isPullToRefresh) }
            try {
                val briefing = repo.getBriefing(date)
                _uiState.update { it.copy(briefing = briefing, isLoading = false, isRefreshing = false, error = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.friendlyMessage()) }
            }
        }
    }
}
