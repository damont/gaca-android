package dev.monts.gaca.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.CloudLinkRepository
import dev.monts.gaca.data.repo.CloudLinkStatus
import dev.monts.gaca.data.repo.LinkAttempt
import dev.monts.gaca.data.repo.PollStep
import dev.monts.gaca.data.repo.UnlinkBlock
import dev.monts.gaca.data.repo.UnlinkResult
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Why the last link attempt ended without linking. */
enum class LinkEnd { Expired, Denied }

data class CloudLinkUiState(
    val loading: Boolean = true,
    /** Null until the first load succeeds. */
    val status: CloudLinkStatus? = null,
    /** Set while a link is in progress: the code to confirm on the cloud page. */
    val attempt: LinkAttempt? = null,
    val starting: Boolean = false,
    val ended: LinkEnd? = null,
    /** Asking "Unlink?" (and, after the server held it back, what would be cut off). */
    val confirmingUnlink: Boolean = false,
    val unlinkBlock: UnlinkBlock? = null,
    val busy: Boolean = false,
    /** What the server said when it finished unlinking (it may say the cloud could not confirm). */
    val notice: String? = null,
    val error: String? = null,
)

/**
 * The "GACA Cloud" card in Settings. The app never talks to the cloud: it asks its own server to link
 * (its `cloud/link` routes), shows the short code the server was given, and polls the server until the person
 * has approved the code in a browser. Everything about the link, including its token, stays on the server.
 */
class CloudLinkViewModel(private val repo: CloudLinkRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudLinkUiState())
    val uiState: StateFlow<CloudLinkUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val status = repo.status()
                _uiState.update { it.copy(loading = false, status = status) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun startLinking() {
        if (_uiState.value.starting) return
        viewModelScope.launch {
            _uiState.update { it.copy(starting = true, error = null, ended = null, notice = null) }
            try {
                val attempt = repo.start()
                _uiState.update { it.copy(starting = false, attempt = attempt) }
                startPolling(attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(starting = false, error = e.friendlyMessage()) }
            }
        }
    }

    /** Stop waiting. The server forgets the unfinished attempt on its own when its code expires. */
    fun cancelLinking() {
        pollJob?.cancel()
        _uiState.update { it.copy(attempt = null, error = null) }
        load()
    }

    /** Ask the server again right now (used after a poll failed, e.g. the phone was offline). */
    fun checkAgain() {
        val attempt = _uiState.value.attempt ?: return
        _uiState.update { it.copy(error = null) }
        startPolling(attempt, firstDelayMillis = 0)
    }

    private fun startPolling(attempt: LinkAttempt, firstDelayMillis: Long = attempt.intervalSeconds * 1000L) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var wait = firstDelayMillis
            while (true) {
                delay(wait)
                if (!Instant.now().isBefore(attempt.expiresAt)) {
                    _uiState.update { it.copy(attempt = null, ended = LinkEnd.Expired) }
                    return@launch
                }
                val step = try {
                    repo.poll(attempt.intervalSeconds)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Stay on the code screen; the person can tap "Check again" when they are back online.
                    _uiState.update { it.copy(error = e.friendlyMessage()) }
                    return@launch
                }
                when (step) {
                    is PollStep.Wait -> wait = step.delayMillis
                    is PollStep.Done -> {
                        _uiState.update { it.copy(attempt = null, status = step.status, error = null) }
                        return@launch
                    }
                    PollStep.Expired -> {
                        _uiState.update { it.copy(attempt = null, ended = LinkEnd.Expired) }
                        return@launch
                    }
                    PollStep.Denied -> {
                        _uiState.update { it.copy(attempt = null, ended = LinkEnd.Denied) }
                        return@launch
                    }
                }
            }
        }
    }

    // ── unlinking ─────────────────────────────────────────────────────

    fun askUnlink() = _uiState.update { it.copy(confirmingUnlink = true, unlinkBlock = null, error = null) }

    fun dismissUnlink() = _uiState.update { it.copy(confirmingUnlink = false, unlinkBlock = null) }

    /** [force] is only sent after the person has seen what unlinking anyway would cut off. */
    fun unlink(force: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                when (val result = repo.unlink(force)) {
                    is UnlinkResult.Done -> {
                        _uiState.update { it.copy(busy = false, confirmingUnlink = false, unlinkBlock = null, notice = result.message) }
                        load()
                    }
                    is UnlinkResult.Blocked -> _uiState.update { it.copy(busy = false, unlinkBlock = result.block) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, confirmingUnlink = false, unlinkBlock = null, error = e.friendlyMessage()) }
            }
        }
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null, ended = null) }
}
