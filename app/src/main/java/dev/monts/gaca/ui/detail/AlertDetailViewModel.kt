package dev.monts.gaca.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.Alert
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.AlertsRepository
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.ConversationMessage
import dev.monts.gaca.data.repo.delegateErrorMessage
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class SnoozeOption(val label: String, val until: Instant)

data class AlertDetailUiState(
    val alert: Alert? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showSnoozeOptions: Boolean = false,
    val noteText: String = "",
    val toast: String? = null,
    /** One-shot: true once the alert has been resolved and the screen should pop back to the inbox. */
    val done: Boolean = false,

    /** This account's configured crew, for the "who handles this" picker. Only
     * shown when there's more than one, so a single-crew-member account sees no extra UI. */
    val crew: List<CrewMember> = emptyList(),
    val selectedCrew: String? = null,

    /** The back-and-forth with the crew member, once this alert has been delegated
     * (`alert.sessionId != null`). Empty and not loading just means never delegated. */
    val conversation: List<ConversationMessage> = emptyList(),
    val conversationLoading: Boolean = false,

    /** "Open full chat" — delegates first if this alert hasn't been yet (that's what
     * creates the session), then this is set so the screen navigates into the real thread. */
    val openingChat: Boolean = false,
    val openChatSessionId: String? = null,
)

class AlertDetailViewModel(
    private val alertId: String,
    private val repo: AlertsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertDetailUiState())
    val uiState: StateFlow<AlertDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val alert = try {
                val alert = repo.getAlert(alertId)
                _uiState.update { it.copy(alert = alert, isLoading = false) }
                alert
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.friendlyMessage()) }
                return@launch
            }

            // Best-effort, and sequenced after the alert so the "prefer the
            // alert's own crew member" match below has it to compare against — an
            // account with no crew configured, or a request that fails,
            // just means no picker; the delegate call itself is still what
            // surfaces "you need to set one up".
            try {
                val crew = authRepository.getCrew().crew
                val preferred = when {
                    alert.crew != null && crew.any { it.name == alert.crew } -> alert.crew
                    else -> crew.firstOrNull { it.isDefault }?.name
                }
                _uiState.update { it.copy(crew = crew, selectedCrew = preferred) }
            } catch (_: Exception) {
                // no picker — see comment above
            }

            val sessionId = alert.sessionId ?: return@launch
            _uiState.update { it.copy(conversationLoading = true) }
            try {
                val conversation = repo.getConversation(sessionId)
                _uiState.update { it.copy(conversation = conversation, conversationLoading = false) }
            } catch (_: Exception) {
                // best-effort, same as the crew picker above — the alert itself already loaded fine
                _uiState.update { it.copy(conversationLoading = false) }
            }
        }
    }

    fun selectCrew(name: String) = _uiState.update { it.copy(selectedCrew = name) }

    fun toggleSnooze() = _uiState.update { it.copy(showSnoozeOptions = !it.showSnoozeOptions) }

    fun setNoteText(text: String) = _uiState.update { it.copy(noteText = text) }

    fun snoozeOptions(): List<SnoozeOption> {
        val now = Instant.now()
        val tomorrowNine = ZonedDateTime.now(ZoneId.systemDefault())
            .plusDays(1)
            .withHour(9).withMinute(0).withSecond(0).withNano(0)
            .toInstant()
        return listOf(
            SnoozeOption("1 hour", now.plus(Duration.ofHours(1))),
            SnoozeOption("3 hours", now.plus(Duration.ofHours(3))),
            SnoozeOption("Tomorrow, 9am", tomorrowNine),
        )
    }

    fun snooze(option: SnoozeOption) = resolve {
        repo.snooze(alertId, option.until.toString())
        "Snoozed for ${option.label.lowercase()}"
    }

    fun pickSuggestedAction(index: Int) = resolve(
        onError = Throwable::delegateErrorMessage,
    ) {
        repo.delegate(alertId, instruction = null, suggestedActionIndex = index, crew = _uiState.value.selectedCrew)
        "Sent to Hermes — new session starting"
    }

    /** Navigates into the real chat thread — delegating first (via the same resolution
     * order the suggested-action picker uses, [_uiState.value.selectedCrew]) only if
     * this alert hasn't been delegated yet, since that's what creates the session at all. */
    fun openFullChat() {
        val alert = _uiState.value.alert ?: return
        val existingSessionId = alert.sessionId
        if (existingSessionId != null) {
            _uiState.update { it.copy(openChatSessionId = existingSessionId) }
            return
        }
        if (_uiState.value.openingChat) return
        viewModelScope.launch {
            _uiState.update { it.copy(openingChat = true, error = null) }
            try {
                val updated = repo.delegate(alertId, instruction = null, suggestedActionIndex = null, crew = _uiState.value.selectedCrew)
                _uiState.update { it.copy(alert = updated, openingChat = false, openChatSessionId = updated.sessionId) }
            } catch (e: Exception) {
                _uiState.update { it.copy(openingChat = false, error = e.delegateErrorMessage()) }
            }
        }
    }

    /** Called right after the screen acts on [AlertDetailUiState.openChatSessionId] — this
     * ViewModel can outlive a round trip into the thread and back, so leaving it set would
     * re-trigger the navigation every time (same class of bug as ChatListViewModel's
     * createdSessionId). */
    fun consumeOpenChat() = _uiState.update { it.copy(openChatSessionId = null) }

    fun dismiss() = resolve {
        val note = _uiState.value.noteText.ifBlank { null }
        repo.dismiss(alertId, note)
        if (note != null) "Ignored — noted" else "Ignored"
    }

    fun act() = resolve {
        val note = _uiState.value.noteText.ifBlank { null }
        repo.act(alertId, note)
        if (note != null) "Marked as handled — noted" else "Marked as handled"
    }

    private fun resolve(onError: (Throwable) -> String = Throwable::friendlyMessage, call: suspend () -> String) {
        viewModelScope.launch {
            try {
                val toastText = call()
                _uiState.update { it.copy(toast = toastText, done = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = onError(e)) }
            }
        }
    }
}
