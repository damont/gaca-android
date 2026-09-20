package dev.monts.gaca.ui.newdelegation

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.ui.theme.crewColor

@Composable
fun NewDelegationScreen(viewModel: NewDelegationViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.done) { if (state.done) onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 20.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ChipBackground)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("NEW DELEGATION", style = MaterialTheme.typography.labelMedium, color = TextMeta)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Start something Hermes should handle, rather than waiting for it to notice on its own.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMeta,
            )

            if (state.error != null) {
                Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot)
            }

            OutlinedTextField(
                value = state.noteText,
                onValueChange = viewModel::setNoteText,
                placeholder = { Text("What should Hermes do?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )

            when {
                state.crewLoading -> CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                state.crew.isEmpty() -> Text(
                    "No crew configured yet — set one up in Settings first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMeta,
                )
                state.crew.size == 1 -> Text(
                    "Sends to ${state.crew.first().name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMeta,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CREW", style = MaterialTheme.typography.labelMedium, color = TextMeta)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.crew.forEach { member ->
                            val selected = member.name == state.selectedCrew
                            val memberColor = crewColor(member.color)
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) memberColor else ChipBackground)
                                    .clickable { viewModel.selectCrew(member.name) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (!selected) {
                                        Box(Modifier.size(7.dp).clip(CircleShape).background(memberColor))
                                    }
                                    Text(
                                        member.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) OnAccent else TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val canSend = state.noteText.isNotBlank() && state.selectedCrew != null && !state.sending
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canSend) HeaderAccent else ChipBackground)
                    .clickable(enabled = canSend, onClick = viewModel::send)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.sending) {
                    CircularProgressIndicator(color = OnAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        "Start delegation",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canSend) OnAccent else TextMeta,
                    )
                }
            }
        }
    }
}
