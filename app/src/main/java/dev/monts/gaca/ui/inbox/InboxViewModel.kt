package dev.monts.gaca.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.Alert
import dev.monts.gaca.data.repo.AlertsRepository
import dev.monts.gaca.data.repo.isStaleTransition
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InboxTab(val effectiveStatus: String, val label: String) {
    NEW("new", "New"),
    SNOOZED("snoozed", "Snoozed"),
    DELEGATED("delegated", "Delegated"),
}

data class InboxUiState(
    val activeTab: InboxTab = InboxTab.NEW,
    val alerts: List<Alert> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class InboxViewModel(private val repo: AlertsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        load(showSpinner = true)
    }

    /** Called on app-foreground and pull-to-refresh — api-contract.md §5, no live stream. */
    fun refresh() = load(showSpinner = false, isPullToRefresh = true)

    fun selectTab(tab: InboxTab) {
        _uiState.update { it.copy(activeTab = tab) }
        load(showSpinner = true)
    }

    fun dismiss(alertId: String) = resolve(alertId) { repo.dismiss(alertId, note = null) }

    fun act(alertId: String) = resolve(alertId) { repo.act(alertId, note = null) }

    private fun resolve(alertId: String, call: suspend () -> Alert) {
        // Optimistic removal — swiping a card away should feel instant, not wait on the network.
        _uiState.update { s -> s.copy(alerts = s.alerts.filterNot { it.id == alertId }) }
        viewModelScope.launch {
            try {
                call()
            } catch (e: Exception) {
                // A 409 means someone/something else already moved this alert (api-contract.md §6) —
                // either way the right response is the same: refetch, don't retry the write.
                load(showSpinner = false)
                if (!e.isStaleTransition()) {
                    _uiState.update { it.copy(error = e.friendlyMessage()) }
                }
            }
        }
    }

    private fun load(showSpinner: Boolean, isPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = showSpinner, isRefreshing = isPullToRefresh) }
            try {
                val tab = _uiState.value.activeTab
                val alerts = repo.listAlerts(status = tab.effectiveStatus)
                _uiState.update { it.copy(alerts = alerts, isLoading = false, isRefreshing = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.friendlyMessage()) }
            }
        }
    }
}
