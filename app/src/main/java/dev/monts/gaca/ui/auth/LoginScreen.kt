package dev.monts.gaca.ui.auth

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dev.monts.gaca.BuildConfig
import dev.monts.gaca.ui.theme.BorderColor
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val form by viewModel.form.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var googleSignInError by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Gaca", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent)
        Text(
            if (form.mode == AuthMode.LOGIN) "Sign in to see your alerts" else "Create an account",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMeta,
        )
        Spacer(Modifier.height(28.dp))

        if (form.mode == AuthMode.REGISTER) {
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = form.email,
            onValueChange = viewModel::setEmail,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = form.password,
            onValueChange = viewModel::setPassword,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )

        val errorText = authState.error ?: googleSignInError
        if (errorText != null) {
            Spacer(Modifier.height(10.dp))
            Text(errorText, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot)
        }

        val formValid = form.email.isNotBlank() && form.password.isNotBlank() &&
            (form.mode == AuthMode.LOGIN || form.name.isNotBlank())

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (formValid) HeaderAccent else HeaderAccent.copy(alpha = 0.4f))
                .clickable(enabled = !form.isSubmitting && formValid) {
                    googleSignInError = null
                    viewModel.submit()
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (form.isSubmitting) {
                CircularProgressIndicator(color = OnAccent, modifier = Modifier.height(18.dp))
            } else {
                Text(
                    if (form.mode == AuthMode.LOGIN) "Sign in" else "Create account",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnAccent,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            if (form.mode == AuthMode.LOGIN) "New here? Create an account" else "Already have an account? Sign in",
            style = MaterialTheme.typography.bodySmall,
            color = TextMeta,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    viewModel.setMode(if (form.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN)
                },
        )

        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PageBackground)
                    .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                    .clickable {
                        googleSignInError = null
                        viewModel.clearError()
                        scope.launch {
                            signInWithGoogle(context, viewModel) { message -> googleSignInError = message }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Sign in with Google", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            }
        }
    }
}

private const val TAG = "GacaGoogleSignIn"

private suspend fun signInWithGoogle(
    context: android.content.Context,
    viewModel: LoginViewModel,
    onError: (String?) -> Unit,
) {
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    try {
        val result = CredentialManager.create(context).getCredential(context, request)
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        viewModel.submitGoogle(googleIdTokenCredential.idToken)
    } catch (e: GetCredentialException) {
        // Logged with the full exception so the real cause (e.g. a Play Services
        // ApiException wrapped inside, often a DEVELOPER_ERROR from a mismatched
        // OAuth client / SHA-1 in Cloud Console) shows up in logcat, not just a
        // generic "framework error" line.
        Log.e(TAG, "Google sign-in failed: ${e.type}", e)
        // A user-cancelled picker isn't a real error — nothing to show for that one.
        if (e.type != "androidx.credentials.TYPE_USER_CANCELED") {
            onError("Google sign-in failed: ${e.errorMessage ?: e.type}")
        }
    } catch (e: GoogleIdTokenParsingException) {
        Log.e(TAG, "Couldn't parse the Google ID token credential", e)
        onError("Google sign-in returned something the app couldn't read.")
    }
}
