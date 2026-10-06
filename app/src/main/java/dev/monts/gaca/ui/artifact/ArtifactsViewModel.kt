package dev.monts.gaca.ui.artifact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.Artifact
import dev.monts.gaca.data.repo.ArtifactRepository
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtifactsUiState(
    val artifacts: List<Artifact> = emptyList(),
    val query: String = "",
    val showArchived: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) {
    val visible: List<Artifact>
        get() = artifacts.filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                it.description.orEmpty().contains(query, ignoreCase = true)
        }
}

/** The Artifacts tab: every page an agent has made for you, in the order you dragged them into
 * (pages you haven't placed yet come first). */
class ArtifactsViewModel(private val repo: ArtifactRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtifactsUiState())
    val uiState: StateFlow<ArtifactsUiState> = _uiState.asStateFlow()

    init {
        load(showSpinner = true)
    }

    fun refresh() = load(showSpinner = false)

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
                val artifacts = repo.list(_uiState.value.showArchived)
                _uiState.update { it.copy(artifacts = artifacts, isLoading = false, isRefreshing = false, error = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.friendlyMessage()) }
            }
        }
    }
}
