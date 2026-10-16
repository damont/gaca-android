package dev.monts.gaca.ui.chat

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.Session
import dev.monts.gaca.data.repo.SessionsRepository
import dev.monts.gaca.util.PhotoPrep
import dev.monts.gaca.util.PreparedPhoto
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatHomeUiState(
    val crew: List<CrewMember> = emptyList(),
    /** Who a new chat goes to. */
    val selectedCrew: String? = null,
    val sessions: List<Session> = emptyList(),
    val isLoading: Boolean = true,
    /** True only while a pull-to-refresh is in flight, so the pull indicator spins until the reload lands. */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val text: String = "",
    val photos: List<PreparedPhoto> = emptyList(),
    val preparingPhotos: Boolean = false,
    val sending: Boolean = false,
    /** One-shot: set once the first message is sent, so the screen can push into the thread. */
    val createdSessionId: String? = null,
) {
    val picked: CrewMember? get() = crew.firstOrNull { it.name == selectedCrew } ?: crew.firstOrNull { it.isDefault } ?: crew.firstOrNull()
}

/**
 * The Chat tab's home: pick who to talk to, see recent chats, and start typing right away.
 * Nothing exists on the server until the first send creates the session.
 */
class ChatHomeViewModel(
    private val sessionsRepository: SessionsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatHomeUiState())
    val uiState: StateFlow<ChatHomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** [isPullToRefresh] keeps the pull indicator spinning until the reload finishes (for at least a beat,
     * so a fast reload is still visible); the automatic refreshes on open and resume stay silent. */
    fun refresh(isPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isPullToRefresh) _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            try {
                val crew = authRepository.getCrew().crew
                val sessions = sessionsRepository.listSessions()
                _uiState.update { it.copy(crew = crew, sessions = sessions, isLoading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.friendlyMessage()) }
            }
            if (isPullToRefresh) {
                delay((MIN_REFRESH_SPINNER_MS - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0))
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun selectCrew(name: String) = _uiState.update { it.copy(selectedCrew = name) }
    fun setText(text: String) = _uiState.update { it.copy(text = text) }
    fun removePhoto(id: String) = _uiState.update { s -> s.copy(photos = s.photos.filterNot { it.id == id }) }

    fun addPhotos(resolver: ContentResolver, uris: List<Uri>) {
        val room = PhotoPrep.MAX_PHOTOS - _uiState.value.photos.size
        if (uris.isEmpty() || room <= 0) return
        _uiState.update { it.copy(preparingPhotos = true, error = null) }
        viewModelScope.launch {
            val wanted = uris.take(room)
            val prepared = wanted.mapNotNull { PhotoPrep.prepare(resolver, it) }
            _uiState.update {
                it.copy(
                    photos = (it.photos + prepared).take(PhotoPrep.MAX_PHOTOS),
                    preparingPhotos = false,
                    error = if (prepared.size < wanted.size) "Couldn't read one of those photos" else it.error,
                )
            }
        }
    }

    fun send() {
        val before = _uiState.value
        val member = before.picked ?: return
        val text = before.text.trim()
        if ((text.isEmpty() && before.photos.isEmpty()) || before.sending || before.preparingPhotos) return
        _uiState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                val session = sessionsRepository.createSession(crew = member.name)
                val photoIds = before.photos.map { sessionsRepository.uploadPhoto(session.id, it.jpeg) }
                sessionsRepository.sendMessage(session.id, text, photoIds)
                _uiState.update { it.copy(sending = false, text = "", photos = emptyList(), createdSessionId = session.id) }
            } catch (e: Exception) {
                _uiState.update { it.copy(sending = false, error = e.friendlyMessage()) }
            }
        }
    }

    /** Called right after the screen acts on [ChatHomeUiState.createdSessionId] (see ChatListViewModel's note). */
    fun consumeCreatedSession() {
        _uiState.update { it.copy(createdSessionId = null) }
        refresh()
    }
}

private const val MIN_REFRESH_SPINNER_MS = 600L
