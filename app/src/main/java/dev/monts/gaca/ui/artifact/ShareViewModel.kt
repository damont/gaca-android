package dev.monts.gaca.ui.artifact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.ArtifactHome
import dev.monts.gaca.data.repo.CloudLinkRepository
import dev.monts.gaca.data.repo.CreatedInvite
import dev.monts.gaca.data.repo.MovePreview
import dev.monts.gaca.data.repo.ShareOptions
import dev.monts.gaca.data.repo.ShareReport
import dev.monts.gaca.data.repo.ShareRepository
import dev.monts.gaca.util.apiError
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The steps of the share sheet. Sharing is by link only, and each step that changes something is confirmed. */
sealed interface SharePhase {
    data object Loading : SharePhase
    /** Linking isn't done (or isn't allowed), so nothing can be shared yet. [message] says why. */
    data class NeedsLink(val message: String, val canOpenSettings: Boolean) : SharePhase
    /** A private page that can't move to the cloud, so it can't be shared. */
    data class CannotMove(val reason: String) : SharePhase
    /** Pick the role, expiry and use limit. For a private page this is also where the move is confirmed. */
    data object Setup : SharePhase
    data object Moving : SharePhase
    data object Checking : SharePhase
    /** The checks are in; one more tap creates the link. */
    data class Review(val report: ShareReport) : SharePhase
    data object Creating : SharePhase
    data class Done(val invite: CreatedInvite) : SharePhase
}

data class ShareUiState(
    val title: String,
    val phase: SharePhase = SharePhase.Loading,
    val options: ShareOptions = ShareOptions(),
    /** Set while the page is private on this server: what moving it to the cloud does. */
    val preview: MovePreview? = null,
    val acknowledged: Boolean = false,
    /** The page is in the cloud now (it was, or the move just finished). */
    val inCloud: Boolean = false,
    val error: String? = null,
) {
    /** The move needs the person's tick when something is left behind. */
    val canContinue: Boolean
        get() = phase == SharePhase.Setup && (inCloud || (preview?.canMove == true && (!preview.requiresAcknowledgement || acknowledged)))
}

/**
 * Share one page by link. If the page is private on this server it is first moved to the cloud (sharing needs it
 * there), which is confirmed with what would be left behind. Then the cloud checks it, and a final tap creates the link.
 * The app only talks to its own server; [onMoved] tells the list to refresh once the page has moved.
 */
class ShareViewModel(
    private val artifactId: String,
    title: String,
    home: ArtifactHome,
    private val shareRepo: ShareRepository,
    private val linkRepo: CloudLinkRepository,
    private val onMoved: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState(title = title, inCloud = home == ArtifactHome.Cloud))
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        run(SharePhase.Loading) {
            val link = linkRepo.status()
            when {
                !link.configured -> SharePhase.NeedsLink("GACA Cloud isn't set up on this server, so pages can't be shared yet.", false)
                !link.isLinked -> SharePhase.NeedsLink("Link your server to GACA Cloud first (Settings), then come back to share.", true)
                link.scope == "read" -> SharePhase.NeedsLink(
                    "Your cloud link is read-only, so it can't share. Unlink and link again in Settings.", true,
                )
                _uiState.value.inCloud -> SharePhase.Setup
                else -> {
                    val preview = shareRepo.preview(artifactId)
                    _uiState.update { it.copy(preview = preview) }
                    if (preview.canMove) SharePhase.Setup else SharePhase.CannotMove(preview.blockedText.orEmpty())
                }
            }
        }
    }

    fun setOptions(options: ShareOptions) = _uiState.update { it.copy(options = options) }

    fun setAcknowledged(value: Boolean) = _uiState.update { it.copy(acknowledged = value) }

    /** Step 1 (private pages only): move to the cloud, then check. For a page already in the cloud: just check. */
    fun continueToChecks() {
        val state = _uiState.value
        if (!state.canContinue) return
        if (state.inCloud) {
            runChecks()
            return
        }
        run(SharePhase.Moving, onError = SharePhase.Setup) {
            shareRepo.moveToCloud(artifactId, acknowledged = state.acknowledged)
            _uiState.update { it.copy(inCloud = true) }
            onMoved()
            checkNow()
        }
    }

    private fun runChecks() = run(SharePhase.Checking, onError = SharePhase.Setup) { checkNow() }

    private suspend fun checkNow(): SharePhase = SharePhase.Review(shareRepo.check(artifactId))

    fun backToSetup() = _uiState.update { it.copy(phase = SharePhase.Setup, error = null) }

    /** The last tap: make the link. Blocked pages cannot get here. */
    fun createLink() {
        val review = _uiState.value.phase as? SharePhase.Review ?: return
        if (review.report.blocked) return
        val options = _uiState.value.options
        run(SharePhase.Creating, onError = review) { SharePhase.Done(shareRepo.createInvite(artifactId, options)) }
    }

    /** Runs one step: shows [during], then the phase the step returns; on failure shows the reason and goes to [onError]. */
    private fun run(during: SharePhase, onError: SharePhase? = null, step: suspend () -> SharePhase) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = during, error = null) }
            try {
                val next = step()
                _uiState.update { it.copy(phase = next) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.shareMessage()
                _uiState.update {
                    it.copy(
                        phase = onError ?: SharePhase.NeedsLink(message, false), error = message.takeIf { onError != null },
                    )
                }
            }
        }
    }

    private fun Throwable.shareMessage(): String {
        val err = apiError()
        // A failed move always leaves the page on this server untouched (the server says so with local_data_changed).
        val untouched = err?.info?.get("local_data_changed")?.toString() == "false"
        return friendlyMessage() + if (untouched) " Your page here was not changed." else ""
    }
}
