package dev.monts.gaca.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.Alert
import dev.monts.gaca.data.repo.AlertsRepository
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
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
    val showDelegateInput: Boolean = false,
    val delegateText: String = "",
    val noteText: String = "",
    val toast: String? = null,
    /** One-shot: true once the alert has been resolved and the screen should pop back to the inbox. */
    val done: Boolean = false,
)

class AlertDetailViewModel(
    private val alertId: String,
    private val repo: AlertsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertDetailUiState())
    val uiState: StateFlow<AlertDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val alert = repo.getAlert(alertId)
                _uiState.update { it.copy(alert = alert, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun toggleSnooze() = _uiState.update {
        it.copy(showSnoozeOptions = !it.showSnoozeOptions, showDelegateInput = false)
    }

    fun toggleDelegate() = _uiState.update {
        it.copy(showDelegateInput = !it.showDelegateInput, showSnoozeOptions = false)
    }

    fun setDelegateText(text: String) = _uiState.update { it.copy(delegateText = text) }
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
        onError = ::delegateErrorMessage,
    ) {
        repo.delegate(alertId, instruction = null, suggestedActionIndex = index)
        "Sent to Hermes — new session starting"
    }

    fun sendDelegate() {
        val text = _uiState.value.delegateText.trim()
        if (text.isEmpty()) return
        resolve(onError = ::delegateErrorMessage) {
            repo.delegate(alertId, instruction = text, suggestedActionIndex = null)
            "Sent to Hermes — new session starting"
        }
    }

    /** api-contract.md §6: a 400 on /delegate means this account has no agent configured yet. */
    private fun delegateErrorMessage(e: Throwable): String =
        if (e is HttpException && e.code() == 400) {
            "Set up your agent in the web app's profile page first, then try delegating again."
        } else {
            e.friendlyMessage()
        }

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
