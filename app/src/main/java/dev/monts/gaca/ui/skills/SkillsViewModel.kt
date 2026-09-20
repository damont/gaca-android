package dev.monts.gaca.ui.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.Skill
import dev.monts.gaca.data.repo.SessionsRepository
import dev.monts.gaca.data.repo.SkillsRepository
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillsUiState(
    val crew: List<CrewMember> = emptyList(),
    val crewLoading: Boolean = true,
    val selectedCrew: String? = null,
    val skills: List<Skill> = emptyList(),
    val skillsLoading: Boolean = false,
    val error: String? = null,

    /** Filters [skills] by name, client-side — the list is already small and fully
     * loaded, so there's no reason to round-trip this to the server. */
    val searchQuery: String = "",

    /** Tapping a skill starts a new session with the selected crew member and
     * pre-fills a message naming it — this is that in flight. */
    val startingChatFor: String? = null,
    val openChatSessionId: String? = null,
    val openChatPrefill: String = "",
)

/** The crew-selector segmented row at top, then `GET /crew/{name}/skills` for whichever
 * member is selected — read-only, live from that crew member's own Hermes every time
 * (api-contract.md §8), never cached client-side either. */
class SkillsViewModel(
    private val authRepository: AuthRepository,
    private val skillsRepository: SkillsRepository,
    private val sessionsRepository: SessionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillsUiState())
    val uiState: StateFlow<SkillsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(crewLoading = true, error = null) }
            try {
                val crew = authRepository.getCrew().crew
                val default = crew.firstOrNull { it.isDefault }?.name ?: crew.firstOrNull()?.name
                _uiState.update { it.copy(crew = crew, crewLoading = false, selectedCrew = default) }
                default?.let { loadSkills(it) }
            } catch (e: Exception) {
                _uiState.update { it.copy(crewLoading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun selectCrew(name: String) {
        if (name == _uiState.value.selectedCrew) return
        _uiState.update { it.copy(selectedCrew = name) }
        loadSkills(name)
    }

    private fun loadSkills(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(skillsLoading = true, error = null) }
            try {
                val skills = skillsRepository.getSkills(name)
                _uiState.update { it.copy(skills = skills, skillsLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(skillsLoading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    /** Starts a new session with the currently selected crew member and pre-fills a
     * message naming this skill — the user still types whatever else and sends it. */
    fun openSkillChat(skill: Skill) {
        val crewName = _uiState.value.selectedCrew ?: return
        if (_uiState.value.startingChatFor != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(startingChatFor = skill.name, error = null) }
            try {
                val session = sessionsRepository.createSession(crew = crewName)
                _uiState.update {
                    it.copy(
                        startingChatFor = null,
                        openChatSessionId = session.id,
                        openChatPrefill = "Use the ${skill.name} skill: ",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(startingChatFor = null, error = e.friendlyMessage()) }
            }
        }
    }

    /** Called right after the screen acts on [SkillsUiState.openChatSessionId] — this
     * ViewModel outlives navigating into the thread and back (same class of bug as
     * ChatListViewModel's createdSessionId), so leaving it set would re-fire the
     * navigation every time you return to this tab. */
    fun consumeOpenChat() = _uiState.update { it.copy(openChatSessionId = null, openChatPrefill = "") }
}
