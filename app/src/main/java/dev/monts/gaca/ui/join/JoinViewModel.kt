package dev.monts.gaca.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.InvitePreview
import dev.monts.gaca.data.repo.JoinProblem
import dev.monts.gaca.data.repo.JoinRepository
import dev.monts.gaca.data.repo.JoinedPage
import dev.monts.gaca.data.repo.LINK_DEAD_TEXT
import dev.monts.gaca.data.repo.joinProblemFor
import dev.monts.gaca.util.apiError
import dev.monts.gaca.util.friendlyMessage
import dev.monts.gaca.util.parseInviteToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface JoinPhase {
    /** Waiting for a link to be pasted. */
    data object Enter : JoinPhase
    data object Looking : JoinPhase
    /** The preview is on screen; nothing is added until the person taps Trust and add. */
    data class Review(val preview: InvitePreview) : JoinPhase
    data object Joining : JoinPhase
    data class Joined(val page: JoinedPage) : JoinPhase
    /** The server isn't linked to a cloud account, so no link can be looked at. */
    data object NeedsLink : JoinPhase
}

data class JoinUiState(val input: String = "", val phase: JoinPhase = JoinPhase.Enter, val error: String? = null)

/**
 * "Trust and add": look at an invite link, then accept it. The token is kept only here, in memory, from the moment it
 * is read until the screen is gone. It goes to the person's own server, never anywhere else, and is never logged.
 */
class JoinViewModel(private val repo: JoinRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinUiState())
    val uiState: StateFlow<JoinUiState> = _uiState.asStateFlow()

    /** The secret part of the link being looked at (not part of the observable state). */
    private var token: String? = null

    fun setInput(text: String) = _uiState.update { it.copy(input = text, error = null) }

    /** A link that arrived by tapping (not pasted): look at it straight away. */
    fun useLink(text: String) {
        setInput(text)
        lookUp()
    }

    fun lookUp() {
        val parsed = parseInviteToken(_uiState.value.input)
        if (parsed == null) {
            _uiState.update { it.copy(error = "That doesn't look like an invite link. Paste the whole link you were sent.") }
            return
        }
        token = parsed
        viewModelScope.launch {
            _uiState.update { it.copy(phase = JoinPhase.Looking, error = null) }
            try {
                val preview = repo.preview(parsed)
                _uiState.update { it.copy(phase = JoinPhase.Review(preview)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e, back = JoinPhase.Enter)
            }
        }
    }

    /** The explicit tap. Only possible from the preview. */
    fun trustAndAdd() {
        val review = _uiState.value.phase as? JoinPhase.Review ?: return
        val t = token ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(phase = JoinPhase.Joining, error = null) }
            try {
                val page = repo.join(t)
                token = null
                _uiState.update { it.copy(input = "", phase = JoinPhase.Joined(page.copy(title = page.title.ifBlank { review.preview.title }))) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e, back = review)
            }
        }
    }

    fun cancelReview() {
        token = null
        _uiState.update { JoinUiState() }
    }

    private fun fail(e: Throwable, back: JoinPhase) {
        when (joinProblemFor(e.apiError()?.code)) {
            JoinProblem.NotLinked -> _uiState.update { it.copy(phase = JoinPhase.NeedsLink, error = null) }
            JoinProblem.LinkDead -> _uiState.update { it.copy(phase = JoinPhase.Enter, error = LINK_DEAD_TEXT) }
            JoinProblem.Other -> _uiState.update { it.copy(phase = back, error = e.friendlyMessage()) }
        }
    }
}
