package dev.monts.gaca.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentBg
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.SeverityWarnBg
import dev.monts.gaca.ui.theme.SeverityWarnDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(ChipBackground).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }

        Text("SERVER", style = MaterialTheme.typography.labelMedium, color = TextMeta)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.baseUrlInput,
            onValueChange = viewModel::setBaseUrlInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API base URL") },
        )
        Text(
            "Ends in /api/. Change this to point at a dev box on your LAN — restart the app afterward for it to take effect.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMeta,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HeaderAccent)
                .clickable(onClick = viewModel::saveBaseUrl)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (state.saved) "Saved" else "Save", style = MaterialTheme.typography.labelLarge, color = OnAccent)
        }

        Spacer(Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR AGENT", style = MaterialTheme.typography.labelMedium, color = TextMeta, modifier = Modifier.weight(1f))
            if (state.agentLoading) {
                CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                val (dot, bg) = if (state.agentConfigured) HeaderAccent to ChipBackground else SeverityWarnDot to SeverityWarnBg
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(
                        if (state.agentConfigured) "Connected" else "Not set up",
                        style = MaterialTheme.typography.labelSmall,
                        color = dot,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "There's no shared agent — each account points GACA at its own Hermes. This is what \"delegate to Hermes\" uses.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMeta,
        )
        if (state.agentConfigured && !state.agentCurrentBaseUrl.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Currently: ${state.agentCurrentBaseUrl}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.agentBaseUrlInput,
            onValueChange = viewModel::setAgentBaseUrlInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Agent base URL") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.agentApiKeyInput,
            onValueChange = viewModel::setAgentApiKeyInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Agent API key") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Text(
            "The key is never shown back once saved — to change either field, re-enter both.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMeta,
            modifier = Modifier.padding(top = 6.dp),
        )
        val agentErrorText = state.agentError
        if (agentErrorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(agentErrorText, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HeaderAccent)
                .clickable(enabled = !state.agentSaving, onClick = viewModel::saveAgentConfig)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.agentSaving) {
                CircularProgressIndicator(color = OnAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(if (state.agentSaved) "Saved" else "Save", style = MaterialTheme.typography.labelLarge, color = OnAccent)
            }
        }

        Spacer(Modifier.height(32.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SeverityUrgentBg)
                .clickable(onClick = viewModel::logout)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Sign out", style = MaterialTheme.typography.labelLarge, color = SeverityUrgentDot)
        }

        Spacer(Modifier.height(24.dp))
    }
}
