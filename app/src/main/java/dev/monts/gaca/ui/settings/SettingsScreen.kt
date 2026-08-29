package dev.monts.gaca.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import dev.monts.gaca.data.repo.AgentProfile
import dev.monts.gaca.ui.theme.BorderColor
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
import dev.monts.gaca.ui.theme.TextSecondary

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
            Text("YOUR AGENTS", style = MaterialTheme.typography.labelMedium, color = TextMeta, modifier = Modifier.weight(1f))
            if (state.agentsLoading) {
                CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                val configured = state.agents.isNotEmpty()
                val (dot, bg) = if (configured) HeaderAccent to ChipBackground else SeverityWarnDot to SeverityWarnBg
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(
                        if (configured) "${state.agents.size} configured" else "Not set up",
                        style = MaterialTheme.typography.labelSmall,
                        color = dot,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "There's no shared agent — each account points GACA at its own Hermes, possibly several named agents sharing one host. Sessions and delegated alerts run against one of these.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMeta,
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.hostInput,
            onValueChange = viewModel::setHostInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Host") },
            placeholder = { Text("http://your-hermes-box") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                .clickable(enabled = !state.hostSaving, onClick = viewModel::saveHost)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            if (state.hostSaving) {
                CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Text("Save host", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            }
        }

        val agentsErrorText = state.agentsError
        if (agentsErrorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(agentsErrorText, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot)
        }

        Spacer(Modifier.height(16.dp))
        state.agents.forEach { agent ->
            AgentRow(
                agent = agent,
                state = state,
                viewModel = viewModel,
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text("ADD AN AGENT", style = MaterialTheme.typography.labelMedium, color = TextMeta)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.newAgentName,
            onValueChange = viewModel::setNewAgentName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            placeholder = { Text("toolshed") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.newAgentPortInput,
            onValueChange = viewModel::setNewAgentPortInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Port") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.newAgentApiKeyInput,
            onValueChange = viewModel::setNewAgentApiKeyInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HeaderAccent)
                .clickable(enabled = !state.newAgentSaving, onClick = viewModel::addAgent)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.newAgentSaving) {
                CircularProgressIndicator(color = OnAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("Add agent", style = MaterialTheme.typography.labelLarge, color = OnAccent)
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

/** One row per configured agent — either its summary (name, port, default badge,
 * actions) or, while `state.editingAgentName == agent.name`, its edit form. */
@Composable
private fun AgentRow(agent: AgentProfile, state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        if (state.editingAgentName == agent.name) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(agent.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMeta,
                    modifier = Modifier.clickable(onClick = viewModel::cancelEditingAgent),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.editPortInput,
                onValueChange = viewModel::setEditPortInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.editApiKeyInput,
                onValueChange = viewModel::setEditApiKeyInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                placeholder = { Text("re-enter to change") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(HeaderAccent)
                    .clickable(enabled = !state.editSaving, onClick = viewModel::saveEditedAgent)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (state.editSaving) {
                    CircularProgressIndicator(color = OnAccent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save", style = MaterialTheme.typography.labelMedium, color = OnAccent)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(agent.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        if (agent.isDefault) {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(ChipBackground).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                Text("default", style = MaterialTheme.typography.labelSmall, color = HeaderAccent)
                            }
                        }
                    }
                    Text("port ${agent.port}", style = MaterialTheme.typography.bodySmall, color = TextMeta)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!agent.isDefault) {
                    Text(
                        "Make default",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.clickable { viewModel.setDefaultAgent(agent.name) },
                    )
                }
                Text(
                    "Edit",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.clickable { viewModel.startEditingAgent(agent) },
                )
                Text(
                    "Remove",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeverityUrgentDot,
                    modifier = Modifier.clickable { viewModel.removeAgent(agent.name) },
                )
            }
        }
    }
}
