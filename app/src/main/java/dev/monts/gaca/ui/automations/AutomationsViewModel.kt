package dev.monts.gaca.ui.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.Automation
import dev.monts.gaca.data.repo.AutomationStatus
import dev.monts.gaca.data.repo.AutomationsRepository
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AutomationFilter(val label: String) { ALL("All"), FAILING("Failing"), PAUSED("Paused") }

data class AutomationsUiState(
    val automations: List<Automation> = emptyList(),
    /** Crew members whose automations couldn't be read — said out loud rather than shown as an empty list. */
    val unavailable: List<String> = emptyList(),
    val filter: AutomationFilter = AutomationFilter.ALL,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) {
    val failingCount: Int get() = automations.count { it.status == AutomationStatus.FAILED }
    val pausedCount: Int get() = automations.count { it.status == AutomationStatus.PAUSED }

    val shown: List<Automation>
        get() = when (filter) {
            AutomationFilter.ALL -> automations.sortedBy { if (it.needsAttention) 0 else 1 } // failures first; the API's order otherwise
            AutomationFilter.FAILING -> automations.filter { it.status == AutomationStatus.FAILED }
            AutomationFilter.PAUSED -> automations.filter { it.status == AutomationStatus.PAUSED }
        }
}

/** The Automations tab: `GET /automations`, read live every time (the API never caches it). */
class AutomationsViewModel(private val repository: AutomationsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationsUiState())
    val uiState: StateFlow<AutomationsUiState> = _uiState.asStateFlow()

    init {
        load(pull = false)
    }

    fun refresh() = load(pull = true)
    fun setFilter(filter: AutomationFilter) = _uiState.update { it.copy(filter = filter) }

    private fun load(pull: Boolean) {
        viewModelScope.launch {
            if (pull) _uiState.update { it.copy(isRefreshing = true) }
            try {
                val list = repository.list()
                _uiState.update {
                    it.copy(automations = list.automations, unavailable = list.unavailable, isLoading = false, isRefreshing = false, error = null)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.friendlyMessage()) }
            }
        }
    }
}
