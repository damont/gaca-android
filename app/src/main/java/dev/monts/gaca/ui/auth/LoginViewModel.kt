package dev.monts.gaca.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, REGISTER }

data class LoginFormState(
    val mode: AuthMode = AuthMode.LOGIN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _form = MutableStateFlow(LoginFormState())
    val form: StateFlow<LoginFormState> = _form.asStateFlow()

    val authState: StateFlow<AuthUiState> = authRepository.state

    fun setMode(mode: AuthMode) = _form.update { it.copy(mode = mode) }
    fun setName(value: String) = _form.update { it.copy(name = value) }
    fun setEmail(value: String) = _form.update { it.copy(email = value) }
    fun setPassword(value: String) = _form.update { it.copy(password = value) }

    fun submit() {
        val f = _form.value
        viewModelScope.launch {
            _form.update { it.copy(isSubmitting = true) }
            if (f.mode == AuthMode.LOGIN) {
                authRepository.login(f.email, f.password)
            } else {
                authRepository.register(f.name, f.email, f.password)
            }
            _form.update { it.copy(isSubmitting = false) }
        }
    }

    fun submitGoogle(idToken: String) {
        viewModelScope.launch { authRepository.googleLogin(idToken) }
    }

    /** Called right before starting a Google sign-in attempt — see AuthRepository.clearError(). */
    fun clearError() = authRepository.clearError()
}
