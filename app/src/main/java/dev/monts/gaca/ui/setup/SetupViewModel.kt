package dev.monts.gaca.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.local.SettingsStore
import dev.monts.gaca.data.local.TokenStore
import dev.monts.gaca.data.repo.SetupRepository
import dev.monts.gaca.util.SetupLink
import dev.monts.gaca.util.friendlyMessage
import dev.monts.gaca.util.normalizeServerUrl
import dev.monts.gaca.util.parseSetupLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PairMode { SCAN, TYPE }

data class SetupState(
    val mode: PairMode = PairMode.SCAN,
    val address: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Pairing this phone with a server: scan the QR code `gaca-api pair` shows (or open its link,
 * or type the address and code by hand). Nothing is saved until the server accepts the code, so a
 * typo never leaves the app pointed at a dead address.
 */
class SetupViewModel(
    private val setup: SetupRepository,
    private val settingsStore: SettingsStore,
    private val tokenStore: TokenStore,
    private val allowHttp: Boolean,
    /** What the server will list this phone as. */
    private val deviceName: String,
    initialAddress: String = "",
    /** Called once the server and credential are saved; the app rebuilds itself around them. */
    private val onPaired: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState(address = initialAddress))
    val state: StateFlow<SetupState> = _state.asStateFlow()

    fun setAddress(v: String) = _state.update { it.copy(address = v, error = null) }
    fun setCode(v: String) = _state.update { it.copy(code = v, error = null) }
    fun showTyping() = _state.update { it.copy(mode = PairMode.TYPE, error = null) }
    fun showScanning() = _state.update { it.copy(mode = PairMode.SCAN, error = null) }
    fun scanFailed() = _state.update {
        it.copy(mode = PairMode.TYPE, error = "Couldn't open the camera scanner. Type the address and code instead.")
    }

    /** A QR code was scanned: it must be one of ours, then pair straight away. */
    fun onScanned(text: String?) {
        val link = parseSetupLink(text)
        if (link == null) {
            _state.update { it.copy(error = "That QR code isn't from a GACA server. Run gaca-api pair on your server.") }
            return
        }
        applyLink(link)
    }

    /** A `gaca://setup` link (scanned, or tapped outside the app). */
    fun applyLink(link: SetupLink) {
        _state.update { it.copy(address = link.url ?: it.address, code = link.code ?: it.code) }
        pair()
    }

    fun pair() {
        val s = _state.value
        val server = normalizeServerUrl(s.address, allowHttp)
        if (server == null) {
            _state.update { it.copy(mode = PairMode.TYPE, error = "That doesn't look like a server address. Try something like gaca.example.com.") }
            return
        }
        if (s.code.isBlank()) {
            _state.update { it.copy(mode = PairMode.TYPE, error = "Enter the pairing code your server printed.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                try {
                    setup.check(server)
                } catch (e: Exception) {
                    throw IllegalStateException("Couldn't reach a GACA server there. ${e.friendlyMessage()}")
                }
                tokenStore.saveToken(setup.pair(server, s.code.trim(), deviceName))
                settingsStore.setBaseUrl(server)
                onPaired()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, mode = PairMode.TYPE, error = e.friendlyMessage()) }
            }
        }
    }
}
