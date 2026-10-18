package dev.monts.gaca.ui.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.Automation
import dev.monts.gaca.data.repo.AutomationRun
import dev.monts.gaca.data.repo.AutomationsRepository
import dev.monts.gaca.data.repo.RunStatus
import dev.monts.gaca.data.repo.SessionsRepository
import dev.monts.gaca.util.clockTime
import dev.monts.gaca.util.dayAndTime
import dev.monts.gaca.util.friendlyMessage
import dev.monts.gaca.util.durationLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AutomationDetailUiState(
    val automation: Automation? = null,
    /** Newest first. */
    val runs: List<AutomationRun> = emptyList(),
    /** Runs whose output is open. The newest run opens by itself when it failed. */
    val expanded: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    /** Set when the run history alone couldn't be read; the rest of the screen is still true. */
    val runsError: String? = null,
    val requestingRun: Boolean = false,
    val runRequested: Boolean = false,
    val startingChat: Boolean = false,
    val openChatSessionId: String? = null,
    val openChatPrefill: String = "",
) {
    /** "8 of 10" from the job's recent runs; null with no history. */
    val successLine: String?
        get() {
            val done = automation?.recent.orEmpty().filter { it.status != RunStatus.RUNNING }
            if (done.isEmpty()) return null
            return "${done.count { it.status == RunStatus.OK }} of ${done.size}"
        }

    /** Median length of the runs that finished, so one slow night doesn't skew it. */
    val typicalRun: String?
        get() {
            val seconds = runs.mapNotNull { it.durationSeconds }.sorted()
            if (seconds.isEmpty()) return null
            return durationLabel(seconds[seconds.size / 2])
        }
}

/** One automation: the job from `GET /automations`, its runs from `.../runs`, and "run now". */
class AutomationDetailViewModel(
    private val crew: String,
    private val id: String,
    private val repository: AutomationsRepository,
    private val sessionsRepository: SessionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationDetailUiState())
    val uiState: StateFlow<AutomationDetailUiState> = _uiState.asStateFlow()

    init {
        load(pull = false)
    }

    fun refresh() = load(pull = true)

    private fun load(pull: Boolean) {
        viewModelScope.launch {
            if (pull) _uiState.update { it.copy(isRefreshing = true) }
            try {
                val automation = repository.list(crew).automations.firstOrNull { it.id == id }
                if (automation == null) {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = "This automation no longer exists.") }
                    return@launch
                }
                _uiState.update { it.copy(automation = automation, isLoading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.friendlyMessage()) }
                return@launch
            }
            try {
                val runs = repository.runs(crew, id)
                _uiState.update { s ->
                    val firstOpen = runs.firstOrNull()?.takeIf { it.status == RunStatus.FAILED }?.id
                    s.copy(runs = runs, runsError = null, expanded = if (s.expanded.isEmpty() && firstOpen != null) setOf(firstOpen) else s.expanded)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(runsError = e.friendlyMessage()) }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun toggle(runId: String) = _uiState.update { it.copy(expanded = if (runId in it.expanded) it.expanded - runId else it.expanded + runId) }

    /** A request, not an instant start: the engine begins on its next tick (about a minute). */
    fun runNow() {
        if (_uiState.value.requestingRun) return
        viewModelScope.launch {
            _uiState.update { it.copy(requestingRun = true, runRequested = false, error = null) }
            try {
                val automation = repository.runNow(crew, id)
                _uiState.update { it.copy(automation = automation, requestingRun = false, runRequested = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(requestingRun = false, error = e.friendlyMessage()) }
            }
        }
    }

    /** Opens a chat with the job's crew member about a failed run, the question already typed (not sent). */
    fun askAbout(run: AutomationRun) {
        val automation = _uiState.value.automation ?: return
        if (_uiState.value.startingChat) return
        viewModelScope.launch {
            _uiState.update { it.copy(startingChat = true, error = null) }
            try {
                val session = sessionsRepository.createSession(crew = crew)
                val whenText = run.startedAt?.let { dayAndTime(it) } ?: "recently"
                val why = run.error ?: run.summary
                val prefill = buildString {
                    append("My \"${automation.name}\" automation failed ($whenText).")
                    if (why != null) append(" It said: $why")
                    append(" What happened, and can you fix it?")
                }
                _uiState.update { it.copy(startingChat = false, openChatSessionId = session.id, openChatPrefill = prefill) }
            } catch (e: Exception) {
                _uiState.update { it.copy(startingChat = false, error = e.friendlyMessage()) }
            }
        }
    }

    /** Called right after the screen acts on [AutomationDetailUiState.openChatSessionId], or coming back would re-open it. */
    fun consumeOpenChat() = _uiState.update { it.copy(openChatSessionId = null, openChatPrefill = "") }
}
