package dev.monts.gaca.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.Alert
import dev.monts.gaca.data.repo.AgentProfile
import dev.monts.gaca.data.repo.AlertsRepository
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.isStaleTransition
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InboxTab(val statuses: List<String>, val label: String) {
    /** `completed` — Hermes finishing a delegated run — belongs here, not in
     * History: finishing isn't the same as being closed out, and it's still
     * triageable exactly like `new` (see api-contract.md §3). */
    NEW(listOf("new", "completed"), "Needs Attention"),
    SNOOZED(listOf("snoozed"), "Snoozed"),
    DELEGATED(listOf("delegated"), "Delegated"),
    /** Terminal alerts — the two settled states, `act` and `dismiss`, read together as one history. */
    HISTORY(listOf("acted", "dismissed"), "History"),
}

data class InboxUiState(
    val activeTab: InboxTab = InboxTab.NEW,
    val alerts: List<Alert> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,

    /** This account's configured agents, to filter the list by who raised each alert. */
    val availableAgents: List<AgentProfile> = emptyList(),
    /** `null` means no filter — every agent (and alerts with none). */
    val agentFilter: String? = null,
)

class InboxViewModel(
    private val repo: AlertsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        load(showSpinner = true)
        viewModelScope.launch {
            // Best-effort — a failed fetch just means no filter row, same as the
            // agent picker elsewhere.
            try {
                val agents = authRepository.getAgents().agents
                _uiState.update { it.copy(availableAgents = agents) }
            } catch (_: Exception) {
                // no filter row
            }
        }
    }

    /** Called on app-foreground and pull-to-refresh — api-contract.md §5, no live stream. */
    fun refresh() = load(showSpinner = false, isPullToRefresh = true)

    fun selectTab(tab: InboxTab) {
        _uiState.update { it.copy(activeTab = tab) }
        load(showSpinner = true)
    }

    fun selectAgentFilter(agent: String?) {
        _uiState.update { it.copy(agentFilter = agent) }
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
                val agentFilter = _uiState.value.agentFilter
                val alerts = repo.listAlerts(statuses = tab.statuses, agent = agentFilter)
                _uiState.update { it.copy(alerts = alerts, isLoading = false, isRefreshing = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.friendlyMessage()) }
            }
        }
    }
}
