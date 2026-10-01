package dev.monts.gaca.ui.chatthread

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.remote.SseEvent
import dev.monts.gaca.data.repo.AlertsRepository
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.ConversationMessage
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.SessionDetail
import dev.monts.gaca.data.repo.SessionsRepository
import dev.monts.gaca.util.PhotoPrep
import dev.monts.gaca.util.PreparedPhoto
import dev.monts.gaca.util.friendlyMessage
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
    /** Just for the header's color/name and tinting bubbles — best-effort, not required to
     * render the thread. */
    val crewMember: CrewMember? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val messageText: String = "",
    val sending: Boolean = false,

    /** Photos picked for the next message — already shrunk and ready to upload. */
    val pendingPhotos: List<PreparedPhoto> = emptyList(),
    /** True while picked photos are still being decoded/shrunk. */
    val preparingPhotos: Boolean = false,

    /** The reply in progress, reassembled from `assistant.delta` events — `null` means no run
     * is currently streaming a reply. Rendered as one more bubble after the persisted messages,
     * then cleared (in favor of the real persisted Message) once `run.completed` lands. */
    val liveAssistantText: String? = null,
    /** A short "what's happening right now" line from `tool.started` — cleared on
     * `tool.completed`/`run.completed`/`run.failed`. `null` when nothing's in flight. */
    val runStatus: String? = null,
    /** Which run's deltas [liveAssistantText] currently holds — a fresh, different run_id
     * means start the accumulator over, never concatenate two different runs' text together. */
    val currentRunId: String? = null,

    /** Bumped whenever the message list changes shape (new send, refresh, or a run settling) —
     * the screen watches this (and `liveAssistantText`) to know when to scroll to the bottom. */
    val newMessageTick: Int = 0,

    /** Set only when this session was opened by delegating an alert (`Session.alertId` —
     * api-contract.md §6's bidirectional link) — the "from alert" pill's label, best-effort. */
    val fromAlertTitle: String? = null,
)

@Serializable
private data class AssistantDeltaPayload(val text: String)

@Serializable
private data class ToolStartedPayload(val tool: String)

@Serializable
private data class RunHeartbeatPayload(@SerialName("idle_seconds") val idleSeconds: Double = 0.0)

@Serializable
private data class RunFailedPayload(val error: String)

/** Every GacaEvent carries this envelope regardless of type (gaca_api/schemas/event.py's
 * `_EventBase`) — decoded separately from the type-specific payload just to read `run_id`. */
@Serializable
private data class EventEnvelope(@SerialName("run_id") val runId: String? = null)

/**
 * Slice 5: the real chat thread. Sending appends the user's own message
 * optimistically (it's stored immediately per api-contract.md §6), and the
 * reply streams in live over [SessionsRepository.events] — `assistant.delta`
 * accumulates into [ChatThreadUiState.liveAssistantText], `tool.started` /
 * `tool.completed` drive [ChatThreadUiState.runStatus], and `run.completed`
 * / `run.failed` are termini: both re-fetch the session so the durable,
 * server-finalized Message replaces the locally-reassembled one, rather than
 * trusting the client's own reconstruction as the source of truth.
 */
class ChatThreadViewModel(
    private val sessionId: String,
    private val sessionsRepository: SessionsRepository,
    private val authRepository: AuthRepository,
    private val alertsRepository: AlertsRepository,
    /** Pre-fills the input, e.g. arriving from a tapped skill in the Skills tab —
     * the user still has to actually send it, this just saves the typing. */
    initialMessageText: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatThreadUiState(messageText = initialMessageText.orEmpty()))
    val uiState: StateFlow<ChatThreadUiState> = _uiState.asStateFlow()

    private val eventJson = Json { ignoreUnknownKeys = true }

    init {
        load()
        viewModelScope.launch {
            sessionsRepository.events(sessionId).collect { event -> handleEvent(event) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            reloadSession()
            _uiState.update { it.copy(isLoading = false) }
            try {
                val crewName = _uiState.value.sessionDetail?.session?.crew
                val member = authRepository.getCrew().crew.firstOrNull { it.name == crewName }
                _uiState.update { it.copy(crewMember = member) }
            } catch (_: Exception) {
                // best-effort — the thread still works with no color/avatar in the header
            }
            val alertId = _uiState.value.sessionDetail?.session?.alertId ?: return@launch
            try {
                val alert = alertsRepository.getAlert(alertId)
                _uiState.update { it.copy(fromAlertTitle = alert.title) }
            } catch (_: Exception) {
                // best-effort, same as the crew lookup above — no pill rather than an error
            }
        }
    }

    private suspend fun reloadSession() {
        try {
            val detail = sessionsRepository.getSession(sessionId)
            _uiState.update { it.copy(sessionDetail = detail, newMessageTick = it.newMessageTick + 1) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.friendlyMessage()) }
        }
    }

    /** Pull-to-refresh: a manual "check whether the assistant replied yet" — the live SSE
     * updates below make this mostly redundant, but it's a harmless, familiar fallback. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            reloadSession()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun setMessageText(text: String) = _uiState.update { it.copy(messageText = text) }

    /** Decodes and shrinks the picked photos (off the main thread), up to [PhotoPrep.MAX_PHOTOS] total. */
    fun addPhotos(resolver: ContentResolver, uris: List<Uri>) {
        val room = PhotoPrep.MAX_PHOTOS - _uiState.value.pendingPhotos.size
        if (uris.isEmpty() || room <= 0) return
        _uiState.update { it.copy(preparingPhotos = true, error = null) }
        viewModelScope.launch {
            val prepared = uris.take(room).mapNotNull { PhotoPrep.prepare(resolver, it) }
            _uiState.update {
                it.copy(
                    pendingPhotos = (it.pendingPhotos + prepared).take(PhotoPrep.MAX_PHOTOS),
                    preparingPhotos = false,
                    error = if (prepared.size < uris.take(room).size) "Couldn't read one of those photos" else it.error,
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

        // Text and photos stay in the draft until the photos are uploaded — if an upload
        // fails nothing is lost and the same tap can simply be retried.
        _uiState.update { it.copy(sending = true, error = null) }

        viewModelScope.launch {
            val photoIds = try {
                photos.map { sessionsRepository.uploadPhoto(sessionId, it.jpeg) }
            } catch (e: Exception) {
                _uiState.update { it.copy(sending = false, error = e.friendlyMessage()) }
                return@launch
            }

            // Clear + append right away — the tap should feel like it did something
            // immediately, not after a network round trip.
            val optimistic = ConversationMessage(
                id = "pending-${System.currentTimeMillis()}",
                role = "user",
                content = text,
                createdAt = Instant.now().toString(),
                localPreviews = photos.map { it.thumbnail },
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
                // Replaces the optimistic entry with the server's own persisted copy. The
                // reply itself streams in over the event collector in init{}, not from here.
                reloadSession()
                _uiState.update { it.copy(sending = false) }
            } catch (e: Exception) {
                // Leave the optimistic message in place rather than silently dropping what
                // was typed — there's no retry affordance yet, but the text isn't lost either.
                _uiState.update { it.copy(sending = false, error = e.friendlyMessage()) }
            }
        }
    }

    private fun handleEvent(event: SseEvent) {
        // The SSE stream replays a session's ENTIRE event history on every fresh
        // connection when it isn't given a Last-Event-ID (gaca-api services/events.py
        // always replays from seq 0 otherwise) — there's no way to ask it to skip
        // straight to "live only." So every time this screen opens, the deltas and
        // tool events for the *already-answered, already-persisted* last reply
        // replay right alongside the REST fetch that already has that same reply —
        // two async paths racing to describe the same message, which is exactly
        // the back-and-forth. The fix: only trust these progress events while we're
        // actually waiting on a reply, i.e. the last persisted message is the
        // user's — once it's the assistant's, any further deltas are either stale
        // replay of that same message (ignore them) or, if truly new, start being
        // trustworthy again the instant `messages` next ends in a user message.
        val awaitingReply = _uiState.value.sessionDetail?.messages?.lastOrNull()?.role != "assistant"

        // A session's replay can include MANY historical runs back to back, not just the
        // one "pending reply" this gate is meant to isolate — e.g. a turn that never got a
        // terminal event for some reason (an interrupted run from earlier testing, say)
        // leaves `awaitingReply` true indefinitely, and every subsequent run replayed after
        // it would otherwise get blindly appended onto the same accumulator, splicing
        // unrelated runs' similar-but-different text together into gibberish. So: every
        // event also carries which run it belongs to (`run_id`), and a delta for a run_id
        // different from the one currently accumulating starts the buffer over instead of
        // concatenating onto it.
        val runId = runCatching { eventJson.decodeFromString<EventEnvelope>(event.data) }.getOrNull()?.runId

        when (event.type) {
            "assistant.delta" -> {
                if (!awaitingReply) return
                val delta = runCatching { eventJson.decodeFromString<AssistantDeltaPayload>(event.data) }.getOrNull() ?: return
                _uiState.update {
                    val startingNewRun = runId != null && runId != it.currentRunId
                    it.copy(
                        liveAssistantText = (if (startingNewRun) "" else it.liveAssistantText.orEmpty()) + delta.text,
                        currentRunId = runId ?: it.currentRunId,
                        newMessageTick = it.newMessageTick + 1,
                    )
                }
            }
            "tool.started" -> {
                if (!awaitingReply) return
                val started = runCatching { eventJson.decodeFromString<ToolStartedPayload>(event.data) }.getOrNull()
                _uiState.update {
                    val startingNewRun = runId != null && runId != it.currentRunId
                    it.copy(
                        runStatus = started?.let { s -> "Using ${s.tool}…" } ?: "Working…",
                        liveAssistantText = if (startingNewRun) "" else it.liveAssistantText.orEmpty(),
                        currentRunId = runId ?: it.currentRunId,
                    )
                }
            }
            // Ephemeral keepalive from the API while the engine is quiet (thinking, or a slow
            // tool) — only shows the run is alive; it carries no state worth keeping.
            "run.heartbeat" -> {
                if (!awaitingReply) return
                val beat = runCatching { eventJson.decodeFromString<RunHeartbeatPayload>(event.data) }.getOrNull()
                val secs = beat?.idleSeconds?.toInt() ?: 0
                _uiState.update { it.copy(runStatus = if (secs >= 30) "Still working… (${secs}s)" else "Still working…") }
            }
            // run.completed/run.failed always run, gate or not — reloadSession() is
            // idempotent, so a stale replayed terminus for an already-persisted run
            // is just a harmless no-op refresh, never a source of a wrong duplicate.
            "tool.completed" -> _uiState.update { it.copy(runStatus = null) }
            "run.completed" -> {
                _uiState.update { it.copy(liveAssistantText = null, runStatus = null, currentRunId = null) }
                viewModelScope.launch { reloadSession() }
            }
            "run.failed" -> {
                val failed = runCatching { eventJson.decodeFromString<RunFailedPayload>(event.data) }.getOrNull()
                _uiState.update {
                    it.copy(liveAssistantText = null, runStatus = null, currentRunId = null, error = failed?.error ?: "The run failed")
                }
                viewModelScope.launch { reloadSession() }
            }
            else -> Unit // e.g. assistant.reasoning — nothing rendered for it yet
        }
    }
}
