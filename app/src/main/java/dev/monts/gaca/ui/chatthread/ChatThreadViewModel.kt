package dev.monts.gaca.ui.chatthread

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.remote.SseEvent
import dev.monts.gaca.data.repo.Alert
import dev.monts.gaca.data.repo.AlertsRepository
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.ConversationMessage
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.SessionDetail
import dev.monts.gaca.data.repo.SessionsRepository
import dev.monts.gaca.util.PhotoPrep
import dev.monts.gaca.util.PreparedPhoto
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

data class ChatThreadUiState(
    val sessionDetail: SessionDetail? = null,
    /** Header avatar/name and the tint of the replies — best-effort, not required to render. */
    val crewMember: CrewMember? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val messageText: String = "",
    val sending: Boolean = false,
    val pendingPhotos: List<PreparedPhoto> = emptyList(),
    val preparingPhotos: Boolean = false,

    /** A run for this session is in flight (from `running` on open, then from the event stream). */
    val running: Boolean = false,
    /** Epoch millis the run began — the live activity row's timer counts from it. */
    val runStartedAtMillis: Long? = null,
    /** The reply so far, reassembled from `assistant.delta` events; only a display of what is
     * streaming, replaced by the server's message when the run completes. */
    val liveText: String = "",
    /** The tool running right now, from `tool.started` until `tool.completed`. */
    val currentTool: String? = null,

    /** Bumped whenever the list changes shape or grows, so the screen knows to scroll down. */
    val newMessageTick: Int = 0,

    /** Set only when the session was opened by delegating an alert — pinned at the top of the thread. */
    val fromAlert: Alert? = null,
)

@Serializable
private data class AssistantDeltaPayload(val text: String)

@Serializable
private data class ToolStartedPayload(val tool: String = "")

@Serializable
private data class RunFailedPayload(val error: String)

/** Every event carries this envelope regardless of type — read separately from the payload just for `run_id`. */
@Serializable
private data class EventEnvelope(@SerialName("run_id") val runId: String? = null)

/**
 * One chat thread: the transcript as the API stores it, plus what the live event stream says is
 * happening right now. The live part is purely transient display — when a run finishes the thread
 * re-reads the session (whose message carries the activity summary and resolved page cards) and
 * drops what it streamed. Nothing is derived from message text here; the API computes it.
 */
class ChatThreadViewModel(
    private val sessionId: String,
    private val sessionsRepository: SessionsRepository,
    private val authRepository: AuthRepository,
    private val alertsRepository: AlertsRepository,
    /** Pre-fills the input, e.g. arriving from a tapped skill — the user still has to send it. */
    initialMessageText: String? = null,
) : ViewModel() {

    private var eventsJob: Job? = null
    private var liveRunId: String? = null

    private val _uiState = MutableStateFlow(ChatThreadUiState(messageText = initialMessageText.orEmpty()))
    val uiState: StateFlow<ChatThreadUiState> = _uiState.asStateFlow()

    private val eventJson = Json { ignoreUnknownKeys = true }

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val detail = reloadSession()
            _uiState.update { it.copy(isLoading = false) }
            if (detail != null) openStream(detail)
            loadCrewAndAlert(detail)
        }
    }

    /** Opens the event stream at the session's `resume_seq`, which replays exactly the in-flight run. */
    private fun openStream(detail: SessionDetail) {
        if (eventsJob != null) return
        _uiState.update {
            it.copy(running = detail.running, runStartedAtMillis = detail.runStartedAt?.let(::parseMillis) ?: it.runStartedAtMillis)
        }
        eventsJob = viewModelScope.launch {
            sessionsRepository.events(sessionId, detail.resumeSeq).collect { handleEvent(it) }
        }
    }

    private suspend fun loadCrewAndAlert(detail: SessionDetail?) {
        try {
            val member = authRepository.getCrew().crew.firstOrNull { it.name == detail?.session?.crew }
            _uiState.update { it.copy(crewMember = member) }
        } catch (_: Exception) {
            // best-effort — the thread works with no color in the header
        }
        val alertId = detail?.session?.alertId ?: return
        try {
            _uiState.update { it.copy(fromAlert = alertsRepository.getAlert(alertId)) }
        } catch (_: Exception) {
            // no pinned card rather than an error
        }
    }

    private suspend fun reloadSession(): SessionDetail? = try {
        val detail = sessionsRepository.getSession(sessionId)
        _uiState.update { it.copy(sessionDetail = detail, newMessageTick = it.newMessageTick + 1) }
        detail
    } catch (e: Exception) {
        _uiState.update { it.copy(error = e.friendlyMessage()) }
        null
    }

    /** Pull-to-refresh: a manual "check whether the assistant replied yet" fallback. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            val detail = reloadSession()
            _uiState.update { it.copy(isRefreshing = false) }
            if (detail != null) openStream(detail)
        }
    }

    fun setMessageText(text: String) = _uiState.update { it.copy(messageText = text) }

    /** Decodes and shrinks the picked photos (off the main thread), up to [PhotoPrep.MAX_PHOTOS] total. */
    fun addPhotos(resolver: ContentResolver, uris: List<Uri>) {
        val room = PhotoPrep.MAX_PHOTOS - _uiState.value.pendingPhotos.size
        if (uris.isEmpty() || room <= 0) return
        _uiState.update { it.copy(preparingPhotos = true, error = null) }
        viewModelScope.launch {
            val wanted = uris.take(room)
            val prepared = wanted.mapNotNull { PhotoPrep.prepare(resolver, it) }
            _uiState.update {
                it.copy(
                    pendingPhotos = (it.pendingPhotos + prepared).take(PhotoPrep.MAX_PHOTOS),
                    preparingPhotos = false,
                    error = if (prepared.size < wanted.size) "Couldn't read one of those photos" else it.error,
                )
            }
        }
    }

    fun removePhoto(id: String) = _uiState.update { s -> s.copy(pendingPhotos = s.pendingPhotos.filterNot { it.id == id }) }

    suspend fun loadImage(url: String) = sessionsRepository.loadImage(url)

    fun send() {
        val before = _uiState.value
        val text = before.messageText.trim()
        val photos = before.pendingPhotos
        if ((text.isEmpty() && photos.isEmpty()) || before.sending || before.preparingPhotos) return

        // Text and photos stay in the composer until the photos are uploaded — if an upload
        // fails nothing is lost and the same tap can simply be retried.
        _uiState.update { it.copy(sending = true, error = null) }

        viewModelScope.launch {
            val photoIds = try {
                photos.map { sessionsRepository.uploadPhoto(sessionId, it.jpeg) }
            } catch (e: Exception) {
                _uiState.update { it.copy(sending = false, error = e.friendlyMessage()) }
                return@launch
            }

            // Shown dimmed right away; the server's copy replaces it on the reload below.
            val optimistic = ConversationMessage(
                id = "pending-${System.currentTimeMillis()}",
                role = "user",
                content = text,
                createdAt = Instant.now().toString(),
                localPreviews = photos.map { it.thumbnail },
                pending = true,
            )
            _uiState.update { state ->
                val detail = state.sessionDetail ?: return@update state
                state.copy(
                    sessionDetail = detail.copy(messages = detail.messages + optimistic),
                    messageText = "",
                    pendingPhotos = emptyList(),
                    newMessageTick = state.newMessageTick + 1,
                )
            }

            try {
                sessionsRepository.sendMessage(sessionId, text, photoIds)
                _uiState.update {
                    it.copy(running = true, runStartedAtMillis = it.runStartedAtMillis ?: System.currentTimeMillis())
                }
                val detail = reloadSession()
                _uiState.update { it.copy(sending = false) }
                if (detail != null) openStream(detail)
            } catch (e: Exception) {
                // Drop the placeholder and give the text back, so a retry is one tap.
                _uiState.update { s ->
                    s.copy(
                        sending = false,
                        error = e.friendlyMessage(),
                        messageText = text,
                        pendingPhotos = photos,
                        sessionDetail = s.sessionDetail?.let { d -> d.copy(messages = d.messages.filterNot { it.id == optimistic.id }) },
                    )
                }
            }
        }
    }

    private fun handleEvent(event: SseEvent) {
        val runId = runCatching { eventJson.decodeFromString<EventEnvelope>(event.data) }.getOrNull()?.runId
        when (event.type) {
            "assistant.delta" -> {
                val delta = runCatching { eventJson.decodeFromString<AssistantDeltaPayload>(event.data) }.getOrNull() ?: return
                beginRun(runId)
                _uiState.update { it.copy(liveText = it.liveText + delta.text, newMessageTick = it.newMessageTick + 1) }
            }
            "tool.started" -> {
                val started = runCatching { eventJson.decodeFromString<ToolStartedPayload>(event.data) }.getOrNull()
                beginRun(runId)
                _uiState.update { it.copy(currentTool = started?.tool?.takeIf { t -> t.isNotEmpty() }) }
            }
            "tool.completed" -> {
                beginRun(runId)
                _uiState.update { it.copy(currentTool = null) }
            }
            // Keepalive while the engine is quiet — only shows the run is alive.
            "run.heartbeat" -> beginRun(runId)
            "run.completed" -> finishRun(null)
            "run.failed" -> finishRun(runCatching { eventJson.decodeFromString<RunFailedPayload>(event.data) }.getOrNull()?.error ?: "The run failed")
            else -> Unit // e.g. assistant.reasoning — nothing rendered for it
        }
    }

    /** A different run id means a fresh run: start the live text over, never splice two runs together. */
    private fun beginRun(runId: String?) {
        val fresh = runId != liveRunId
        liveRunId = runId
        _uiState.update {
            it.copy(
                running = true,
                runStartedAtMillis = if (it.running) it.runStartedAtMillis ?: System.currentTimeMillis() else System.currentTimeMillis(),
                liveText = if (fresh) "" else it.liveText,
                currentTool = if (fresh) null else it.currentTool,
            )
        }
    }

    /** The live turn stays up until the server's message arrives, so the reply never blinks out. */
    private fun finishRun(error: String?) {
        viewModelScope.launch {
            reloadSession()
            liveRunId = null
            _uiState.update {
                it.copy(running = false, runStartedAtMillis = null, liveText = "", currentTool = null, error = error ?: it.error)
            }
        }
    }

    private fun parseMillis(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
}
