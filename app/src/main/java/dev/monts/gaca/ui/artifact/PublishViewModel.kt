package dev.monts.gaca.ui.artifact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.CloudLinkRepository
import dev.monts.gaca.data.repo.PublishListing
import dev.monts.gaca.data.repo.PublishRepository
import dev.monts.gaca.data.repo.PublishReview
import dev.monts.gaca.data.repo.PublishedPage
import dev.monts.gaca.util.apiError
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The steps of the publish sheet. Nothing goes public until the last tap, after the preview. */
sealed interface PublishPhase {
    data object Loading : PublishPhase
    data class NeedsLink(val message: String, val canOpenSettings: Boolean) : PublishPhase
    /** Title, description and where it shows up. */
    data object Form : PublishPhase
    data object Checking : PublishPhase
    /** The cloud's preview is in. One more tap publishes exactly this. */
    data class Review(val review: PublishReview, val listing: PublishListing) : PublishPhase
    data object Publishing : PublishPhase
    data class Done(val result: PublishedPage) : PublishPhase
}

data class PublishUiState(
    val phase: PublishPhase = PublishPhase.Loading,
    val listing: PublishListing,
    val alreadyPublished: Boolean,
    val error: String? = null,
)

/**
 * Publish one cloud page as a public template. The cloud strips it (page and shapes, never data), shows the exact
 * result and a hash of it, and only publishes what matches that hash after the owner confirms. The listing text is
 * what the owner types here, never the page's private description.
 */
class PublishViewModel(
    private val artifactId: String,
    title: String,
    alreadyPublished: Boolean,
    private val publishRepo: PublishRepository,
    private val linkRepo: CloudLinkRepository,
    private val onPublished: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PublishUiState(listing = PublishListing(title), alreadyPublished = alreadyPublished))
    val uiState: StateFlow<PublishUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        run(PublishPhase.Loading) {
            val link = linkRepo.status()
            when {
                !link.configured -> PublishPhase.NeedsLink("GACA Cloud isn't set up on this server, so pages can't be published yet.", false)
                !link.isLinked -> PublishPhase.NeedsLink("Link your server to GACA Cloud first (Settings), then come back to publish.", true)
                link.scope == "read" -> PublishPhase.NeedsLink(
                    "Your cloud link is read-only, so it can't publish. Unlink and link again in Settings.", true,
                )
                else -> PublishPhase.Form
            }
        }
    }

    fun setListing(listing: PublishListing) = _uiState.update { it.copy(listing = listing) }

    fun backToForm() = _uiState.update { it.copy(phase = PublishPhase.Form, error = null) }

    /** Step 1: ask the cloud what would be published. Nothing is published yet. */
    fun preview() {
        val listing = _uiState.value.listing
        if (!listing.isValid) return
        run(PublishPhase.Checking, onError = PublishPhase.Form) {
            PublishPhase.Review(publishRepo.check(artifactId, listing), listing)
        }
    }

    /** The last tap. Publishes the listing that was previewed, with the hash the cloud gave. Blocked pages can't get here. */
    fun publish() {
        val review = _uiState.value.phase as? PublishPhase.Review ?: return
        val hash = review.review.hash ?: return
        if (!review.review.canPublish) return
        run(PublishPhase.Publishing, onError = review) {
            val result = publishRepo.publish(artifactId, review.listing, hash)
            onPublished()
            PublishPhase.Done(result)
        }
    }

    private fun run(during: PublishPhase, onError: PublishPhase? = null, step: suspend () -> PublishPhase) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = during, error = null) }
            try {
                val next = step()
                _uiState.update { it.copy(phase = next) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.publishMessage()
                val stale = e.apiError()?.code == "preview_mismatch"
                _uiState.update {
                    it.copy(
                        phase = if (stale) PublishPhase.Form else onError ?: PublishPhase.NeedsLink(message, false),
                        error = message.takeIf { onError != null },
                    )
                }
            }
        }
    }

    private fun Throwable.publishMessage(): String = when (apiError()?.code) {
        // The page or the listing changed between the preview and the tap: nothing was published.
        "preview_mismatch" -> "The page changed since you previewed it, so nothing was published. Preview it again."
        else -> friendlyMessage()
    }
}
