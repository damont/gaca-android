package dev.monts.gaca.ui.artifact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.Artifact
import dev.monts.gaca.data.repo.ArtifactFilter
import dev.monts.gaca.data.repo.ArtifactRepository
import dev.monts.gaca.data.repo.Template
import dev.monts.gaca.data.repo.TemplatesRepository
import dev.monts.gaca.data.repo.matches
import dev.monts.gaca.util.apiError
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the gallery cannot be shown: the server is not linked to a cloud account (go to Settings), or something else failed. */
sealed interface GalleryProblem {
    data object NotLinked : GalleryProblem
    data object NotConfigured : GalleryProblem
    data class Failed(val message: String) : GalleryProblem
}

data class GalleryUiState(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val templates: List<Template> = emptyList(),
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    val problem: GalleryProblem? = null,
    /** The template being copied right now. */
    val installingId: String? = null,
)

data class ArtifactsUiState(
    val artifacts: List<Artifact> = emptyList(),
    val filter: ArtifactFilter = ArtifactFilter.Mine,
    /** The cloud could not be asked, so shared pages are as they were last time. */
    val stale: Boolean = false,
    val gallery: GalleryUiState = GalleryUiState(),
    /** A one-line result, e.g. that a template was added. */
    val notice: String? = null,
    val query: String = "",
    val showArchived: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) {
    val visible: List<Artifact>
        get() = artifacts.filter { it.matches(filter) }.filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                it.description.orEmpty().contains(query, ignoreCase = true)
        }

    val visibleTemplates: List<Template> get() = gallery.templates.filter { it.matches(query) }
}

/** The Artifacts tab: every page an agent has made for you, in the order you dragged them into
 * (pages you haven't placed yet come first). */
class ArtifactsViewModel(
    private val repo: ArtifactRepository,
    private val templates: TemplatesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtifactsUiState())
    val uiState: StateFlow<ArtifactsUiState> = _uiState.asStateFlow()

    init {
        load(showSpinner = true)
    }

    fun refresh() = if (_uiState.value.filter == ArtifactFilter.Gallery) loadGallery() else load(showSpinner = false)

    fun setFilter(filter: ArtifactFilter) {
        _uiState.update { it.copy(filter = filter, notice = null) }
        if (filter == ArtifactFilter.Gallery && !_uiState.value.gallery.loaded) loadGallery()
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    // ── gallery ────────────────────────────────────────────────────────

    fun loadGallery() {
        viewModelScope.launch {
            _uiState.update { it.copy(gallery = it.gallery.copy(loading = true, problem = null)) }
            try {
                val page = templates.page()
                _uiState.update {
                    it.copy(gallery = GalleryUiState(loaded = true, templates = page.templates, nextCursor = page.nextCursor))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(gallery = it.gallery.copy(loading = false, loaded = true, problem = e.toGalleryProblem())) }
            }
        }
    }

    fun loadMoreGallery() {
        val g = _uiState.value.gallery
        val cursor = g.nextCursor ?: return
        if (g.loadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(gallery = it.gallery.copy(loadingMore = true)) }
            try {
                val page = templates.page(cursor)
                _uiState.update {
                    it.copy(gallery = it.gallery.copy(
                        loadingMore = false,
                        templates = (it.gallery.templates + page.templates).distinctBy { t -> t.id },
                        nextCursor = page.nextCursor,
                    ))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(gallery = it.gallery.copy(loadingMore = false), notice = e.friendlyMessage()) }
            }
        }
    }

    /** Makes a private copy on the person's server (the UI has asked them first), then shows it under Mine. */
    fun install(template: Template) {
        if (_uiState.value.gallery.installingId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(gallery = it.gallery.copy(installingId = template.id), notice = null) }
            try {
                templates.install(template.id)
                _uiState.update {
                    it.copy(gallery = it.gallery.copy(installingId = null), filter = ArtifactFilter.Mine,
                        notice = "Added “${template.title}” to your pages. It is private until you share it.")
                }
                load(showSpinner = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(gallery = it.gallery.copy(installingId = null), notice = e.friendlyMessage()) }
            }
        }
    }

    private fun Throwable.toGalleryProblem(): GalleryProblem = when (apiError()?.code) {
        "cloud_not_linked" -> GalleryProblem.NotLinked
        "cloud_not_configured" -> GalleryProblem.NotConfigured
        else -> GalleryProblem.Failed(friendlyMessage())
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun setShowArchived(show: Boolean) {
        _uiState.update { it.copy(showArchived = show) }
        load(showSpinner = true)
    }

    /** Moves a row while it is being dragged; nothing is saved until [saveOrder]. Dragging is only
     * offered when no search filter is active, so indexes here are positions in the full list. */
    fun move(from: Int, to: Int) = _uiState.update { state ->
        if (from == to || from !in state.artifacts.indices || to !in state.artifacts.indices) {
            state
        } else {
            state.copy(artifacts = state.artifacts.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    /** Called when a drag ends: the API remembers the order. */
    fun saveOrder() {
        val ids = _uiState.value.artifacts.map { it.id }
        viewModelScope.launch {
            try {
                repo.reorder(ids)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.friendlyMessage()) }
                load(showSpinner = false) // put back what the server actually has
            }
        }
    }

    fun moveToTop(artifact: Artifact) {
        val index = _uiState.value.artifacts.indexOfFirst { it.id == artifact.id }
        if (index > 0) {
            move(index, 0)
            saveOrder()
        }
    }

    fun archive(artifact: Artifact) = act { repo.setArchived(artifact.id, !artifact.archived) }

    fun delete(artifact: Artifact) = act { repo.delete(artifact.id) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                load(showSpinner = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.friendlyMessage()) }
            }
        }
    }

    private fun load(showSpinner: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = showSpinner, isRefreshing = !showSpinner) }
            try {
                val list = repo.list(_uiState.value.showArchived)
                _uiState.update { it.copy(artifacts = list.items, stale = list.stale, isLoading = false, isRefreshing = false, error = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.friendlyMessage()) }
            }
        }
    }
}
