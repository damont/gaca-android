package dev.monts.gaca.ui.artifact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.Artifact
import dev.monts.gaca.data.repo.ArtifactRepository
import dev.monts.gaca.data.repo.SessionsRepository
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtifactViewState(
    val artifact: Artifact? = null,
    /** The page, ready to load into the WebView (with the SDK and policy wrapped around it). */
    val document: String? = null,
    /** Bumped on reload so the screen loads the document again even though it's equal. */
    val loadCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val chatSessionId: String? = null,
    val chatPrefill: String? = null,
    val finished: Boolean = false,
)

/** One open artifact page: fetches it (already wrapped by the API), and turns `app.ask(...)` from the page into a chat
 * with an unsent draft (in the chat the artifact was made for, or a new one with its crew member). */
class ArtifactViewModel(
    private val artifactId: String,
    private val repo: ArtifactRepository,
    private val sessions: SessionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtifactViewState())
    val uiState: StateFlow<ArtifactViewState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val artifact = repo.get(artifactId)
                val page = repo.page(artifactId)
                _uiState.update {
                    it.copy(
                        artifact = artifact,
                        document = page,
                        loadCount = it.loadCount + 1,
                        isLoading = false,
                    )
                }
                repo.markOpened(artifactId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.friendlyMessage()) }
            }
        }
    }

    /** The page asked to talk to the crew member: open a chat with [text] waiting, unsent. */
    /** The chat made for this page the first time it asked, so asking again reuses it instead of piling up chats. */
    private var askedSessionId: String? = null

    fun ask(text: String) {
        viewModelScope.launch {
            val artifact = _uiState.value.artifact ?: return@launch
            try {
                val sessionId = artifact.sessionId ?: askedSessionId
                    ?: sessions.createSession(title = artifact.title, crew = artifact.crew).id.also { askedSessionId = it }
                _uiState.update { it.copy(chatSessionId = sessionId, chatPrefill = text) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.friendlyMessage()) }
            }
        }
    }

    fun consumeChat() = _uiState.update { it.copy(chatSessionId = null, chatPrefill = null) }

    fun archive() {
        viewModelScope.launch {
            try {
                repo.setArchived(artifactId, true)
                _uiState.update { it.copy(finished = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.friendlyMessage()) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                repo.delete(artifactId)
                _uiState.update { it.copy(finished = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.friendlyMessage()) }
            }
        }
    }
}
