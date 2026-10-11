package dev.monts.gaca.ui.setup

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta

/** First run, or after "Use a different server": point the app at a server and get in. */
@Composable
fun SetupScreen(viewModel: SetupViewModel) {
    val s by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Gaca", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent)
        Text(
            when (s.step) {
                SetupStep.ADDRESS -> "Where is your server?"
                SetupStep.CLAIM -> "Set up this server"
                SetupStep.SIGN_IN -> "Sign in"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextMeta,
        )
        Spacer(Modifier.height(28.dp))

        when (s.step) {
            SetupStep.ADDRESS -> {
                OutlinedTextField(
                    value = s.address,
                    onValueChange = viewModel::setAddress,
                    label = { Text("Server address") },
                    placeholder = { Text("gaca.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The address your server printed when it was installed, or scan its QR code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMeta,
                )
            }
            SetupStep.CLAIM, SetupStep.SIGN_IN -> {
                Text(s.server.orEmpty().removeSuffix("/api/"), style = MaterialTheme.typography.bodySmall, color = TextMeta)
                Spacer(Modifier.height(12.dp))
                if (s.step == SetupStep.CLAIM) {
                    Field(s.code, viewModel::setCode, "Setup code", ImeAction.Next)
                    Spacer(Modifier.height(12.dp))
                    Field(s.name, viewModel::setName, "Your name", ImeAction.Next)
                    Spacer(Modifier.height(12.dp))
                }
                Field(s.email, viewModel::setEmail, "Email", ImeAction.Next, KeyboardType.Email)
                Spacer(Modifier.height(12.dp))
                Field(s.password, viewModel::setPassword, "Password", ImeAction.Done, KeyboardType.Password, secret = true)
                if (s.step == SetupStep.CLAIM) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This makes you the only person who can use this server. At least 8 characters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMeta,
                    )
                }
            }
        }

        s.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot)
        }

        val ready = when (s.step) {
            SetupStep.ADDRESS -> s.address.isNotBlank()
            SetupStep.CLAIM -> s.code.isNotBlank() && s.name.isNotBlank() && s.email.isNotBlank() && s.password.length >= 8
            SetupStep.SIGN_IN -> s.email.isNotBlank() && s.password.isNotBlank()
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (ready) HeaderAccent else HeaderAccent.copy(alpha = 0.4f))
                .clickable(enabled = ready && !s.busy) {
                    if (s.step == SetupStep.ADDRESS) viewModel.connect() else viewModel.submit()
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (s.busy) {
                CircularProgressIndicator(color = OnAccent, modifier = Modifier.height(18.dp))
            } else {
                Text(
                    when (s.step) {
                        SetupStep.ADDRESS -> "Continue"
                        SetupStep.CLAIM -> "Set up server"
                        SetupStep.SIGN_IN -> "Sign in"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = OnAccent,
                )
            }
        }

        if (s.step != SetupStep.ADDRESS) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Use a different server",
                style = MaterialTheme.typography.bodySmall,
                color = TextMeta,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::back),
            )
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    ime: ImeAction,
    type: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ime),
    )
}
