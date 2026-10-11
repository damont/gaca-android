package dev.monts.gaca.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.local.SettingsStore
import dev.monts.gaca.data.local.TokenStore
import dev.monts.gaca.data.repo.SetupRepository
import dev.monts.gaca.util.SetupLink
import dev.monts.gaca.util.friendlyMessage
import dev.monts.gaca.util.normalizeServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStep { ADDRESS, CLAIM, SIGN_IN }

data class SetupState(
    val step: SetupStep = SetupStep.ADDRESS,
    val address: String = "",
    /** The normalized API base URL, once the server has answered. */
    val server: String? = null,
    val code: String = "",
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * "Which server?" then "set it up" (first time, with the one-time code) or "sign in". Nothing
 * is saved until the server has accepted the account, so a typo never leaves the app pointed
 * at a dead address.
 */
class SetupViewModel(
    private val setup: SetupRepository,
    private val settingsStore: SettingsStore,
    private val tokenStore: TokenStore,
    private val allowHttp: Boolean,
    /** Called once the server and session are saved; the app rebuilds itself around them. */
    private val onConfigured: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    fun setAddress(v: String) = _state.update { it.copy(address = v, error = null) }
    fun setCode(v: String) = _state.update { it.copy(code = v, error = null) }
    fun setName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun setEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun setPassword(v: String) = _state.update { it.copy(password = v, error = null) }

    fun back() = _state.update { it.copy(step = SetupStep.ADDRESS, server = null, error = null) }

    /** A `gaca://setup` link: fill in what it carries and go straight to asking the server. */
    fun applyLink(link: SetupLink) {
        _state.update { it.copy(address = link.url ?: it.address, code = link.code ?: it.code) }
        if (!link.url.isNullOrBlank()) connect()
    }

    fun connect() {
        val server = normalizeServerUrl(_state.value.address, allowHttp)
        if (server == null) {
            _state.update { it.copy(error = "That doesn't look like a server address. Try something like gaca.example.com.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val status = setup.status(server)
                _state.update {
                    it.copy(
                        busy = false,
                        server = server,
                        step = if (status.claimed) SetupStep.SIGN_IN else SetupStep.CLAIM,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "Couldn't reach a GACA server there. ${e.friendlyMessage()}") }
            }
        }
    }

    fun submit() {
        val s = _state.value
        val server = s.server ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val token = if (s.step == SetupStep.CLAIM) {
                    setup.claim(server, s.code.trim(), s.name.trim(), s.email.trim(), s.password)
                } else {
                    setup.signIn(server, s.email.trim(), s.password)
                }
                tokenStore.saveToken(token)
                settingsStore.setBaseUrl(server)
                onConfigured()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.friendlyMessage()) }
            }
        }
    }
}
