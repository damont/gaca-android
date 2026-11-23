package dev.monts.gaca.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.Alert
import dev.monts.gaca.data.repo.AlertsRepository
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.Reminder
import dev.monts.gaca.data.repo.RemindersRepository
import dev.monts.gaca.ui.chatthread.ReminderUndo
import dev.monts.gaca.data.repo.delegateErrorMessage
import dev.monts.gaca.data.repo.isStaleTransition
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertDetailUiState(
    val alert: Alert? = null,
    /** This account's crew, only to colour the crew chip. Best-effort: empty just means a plain chip. */
    val crew: List<CrewMember> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** One-shot: true once the person has closed the alert and the screen should pop back to the inbox. */
    val done: Boolean = false,
    /** True while a tap is being sent. */
    val busy: Boolean = false,
    val openingChat: Boolean = false,
    val openChatSessionId: String? = null,
    /** Pre-fill for the chat's message box. The API only ever gives one when a specific action's
     * prompt was picked; "Chat about this" gives none, because the chat carries the alert itself. */
    val openChatDraft: String? = null,
    /** The reminder a finished "Remind me..." action set up, while it can still be taken back. */
    val suggestedReminder: Reminder? = null,
    val suggestedReminderUndo: ReminderUndo? = null,
)

/**
 * Shows one alert and forwards taps. It decides nothing: which actions can be tapped, what they say,
 * whether the alert closes and which buttons close it all arrive in [Alert.actions] and
 * [Alert.workflow]. The one thing it does is read the alert again every few seconds while the API
 * says an action is running, so the result appears without leaving the screen.
 */
class AlertDetailViewModel(
    private val alertId: String,
    private val repo: AlertsRepository,
    private val authRepository: AuthRepository,
    private val remindersRepository: RemindersRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertDetailUiState())
    val uiState: StateFlow<AlertDetailUiState> = _uiState.asStateFlow()

    private var polling: Job? = null

    init {
        viewModelScope.launch {
            try {
                show(repo.getAlert(alertId))
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.friendlyMessage()) }
            }
            try {
                _uiState.update { it.copy(crew = authRepository.getCrew().crew) }
            } catch (_: Exception) {
                // a plain chip
            }
        }
    }

    /** Read it again — on coming back to the screen (after the mail app, say). */
    fun refresh() {
        viewModelScope.launch {
            try {
                show(repo.getAlert(alertId))
            } catch (_: Exception) {
                // keep what is on screen
            }
        }
    }

    private fun show(alert: Alert) {
        _uiState.update { it.copy(alert = alert, isLoading = false, busy = false, error = null) }
        if (alert.workflow.phase == "working") startPolling()
        findSuggestedReminder(alert)
    }

    private var lookedForReminder = false

    /** Once a "Remind me..." action has finished, find the reminder it made (it carries this alert's id)
     * so the row can offer Undo. Best-effort: with none found the row simply has no Undo. */
    private fun findSuggestedReminder(alert: Alert) {
        val done = alert.actions.any { it.kind == "reminder" && it.state == "done" }
        if (!done || lookedForReminder) return
        lookedForReminder = true
        viewModelScope.launch {
            try {
                val found = remindersRepository.listOpen().firstOrNull { it.sourceAlertId == alertId && it.state == "pending" }
                _uiState.update { it.copy(suggestedReminder = found) }
            } catch (_: Exception) {
                lookedForReminder = false
            }
        }
    }

    /** Undo on the finished reminder action. */
    fun undoSuggestedReminder() {
        val reminder = _uiState.value.suggestedReminder ?: return
        if (_uiState.value.suggestedReminderUndo == ReminderUndo.WORKING) return
        _uiState.update { it.copy(suggestedReminderUndo = ReminderUndo.WORKING) }
        viewModelScope.launch {
            try {
                remindersRepository.cancel(reminder.id)
                _uiState.update { it.copy(suggestedReminderUndo = ReminderUndo.DONE) }
            } catch (_: Exception) {
                _uiState.update { it.copy(suggestedReminderUndo = ReminderUndo.FAILED) }
            }
        }
    }

    private fun startPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (true) {
                delay(POLL_MILLIS)
                val alert = try {
                    repo.getAlert(alertId)
                } catch (_: Exception) {
                    continue
                }
                _uiState.update { it.copy(alert = alert) }
                if (alert.workflow.phase != "working") break
            }
        }
    }

    /** Tap an action. */
    fun run(index: Int) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                show(repo.runAction(alertId, index))
            } catch (e: Exception) {
                // A 409 means it moved underneath us (closed, or something else is running): read it again.
                if (e.isStaleTransition()) refresh() else _uiState.update { it.copy(error = e.delegateErrorMessage()) }
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    /** One of the buttons the API offered for closing the alert. */
    fun close(reason: String) {
        viewModelScope.launch {
            try {
                repo.close(alertId, reason)
                _uiState.update { it.copy(done = true) }
            } catch (e: Exception) {
                if (e.isStaleTransition()) refresh() else _uiState.update { it.copy(error = e.friendlyMessage()) }
            }
        }
    }

    /** "Chat about this": the API opens (or reuses) the alert's chat and the chat carries the alert. */
    fun openChat() {
        if (_uiState.value.openingChat) return
        viewModelScope.launch {
            _uiState.update { it.copy(openingChat = true, error = null) }
            try {
                val result = repo.takeToChat(alertId, suggestedActionIndex = null, crew = null)
                _uiState.update {
                    it.copy(alert = result.alert, openingChat = false, openChatSessionId = result.sessionId, openChatDraft = result.draft)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(openingChat = false, error = e.delegateErrorMessage()) }
            }
        }
    }

    /** Called right after the screen acts on [AlertDetailUiState.openChatSessionId] — this ViewModel can
     * outlive a round trip into the thread and back, so leaving it set would re-open the chat every time. */
    fun consumeOpenChat() = _uiState.update { it.copy(openChatSessionId = null, openChatDraft = null) }

    private companion object {
        const val POLL_MILLIS = 3_000L
    }
}
