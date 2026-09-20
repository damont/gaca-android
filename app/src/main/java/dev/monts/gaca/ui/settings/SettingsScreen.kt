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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.CrewMember
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
import dev.monts.gaca.ui.theme.crewColor

/** The same 6 muted hues gaca-api auto-assigns from (models/user.py's
 * CREW_COLOR_PALETTE) — kept here only as swatch options for the picker; the
 * server is the source of truth for what a crew member's color actually is. */
private val CREW_COLOR_PALETTE = listOf(
    "#4E7093",
    "#6B8F5A",
    "#B97A56",
    "#8A6A9E",
    "#3F8E93",
    "#B0923F",
)

@OptIn(ExperimentalMaterial3Api::class)
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
            Text("YOUR CREW", style = MaterialTheme.typography.labelMedium, color = TextMeta, modifier = Modifier.weight(1f))
            if (state.crewLoading) {
                CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                val configured = state.crew.isNotEmpty()
                val (dot, bg) = if (configured) HeaderAccent to ChipBackground else SeverityWarnDot to SeverityWarnBg
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(
                        if (configured) "${state.crew.size} configured" else "Not set up",
                        style = MaterialTheme.typography.labelSmall,
                        color = dot,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Named profiles on your connected Hermes agent. Sessions and delegated alerts run against one of these — the default, unless something more specific applies.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMeta,
        )

        val crewErrorText = state.crewError
        if (crewErrorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(crewErrorText, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot)
        }

        Spacer(Modifier.height(16.dp))
        state.crew.forEach { member ->
            CrewRow(
                member = member,
                state = state,
                viewModel = viewModel,
            )
            Spacer(Modifier.height(8.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, BorderColor, RoundedCornerShape(12.dp))
                .clickable(onClick = viewModel::openAddCrewSheet)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Text("Add crew member", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
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

    if (state.showAddCrewSheet) {
        AddCrewMemberSheet(state, viewModel)
    }
}

/** "9. Add crew member (sheet)" — name/port/API key, plus the color-swatch row: leave
 * it on "Auto" and the server auto-assigns one from its own palette (first swatch not
 * already in use); tap a swatch to override before saving. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCrewMemberSheet(state: SettingsUiState, viewModel: SettingsViewModel) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = viewModel::dismissAddCrewSheet, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add a crew member", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

            val errorText = state.crewError
            if (errorText != null) {
                Text(errorText, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot)
            }

            OutlinedTextField(
                value = state.newCrewName,
                onValueChange = viewModel::setNewCrewName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                placeholder = { Text("toolshed") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.newCrewPortInput,
                    onValueChange = viewModel::setNewCrewPortInput,
                    modifier = Modifier.weight(1f),
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = state.newCrewApiKeyInput,
                    onValueChange = viewModel::setNewCrewApiKeyInput,
                    modifier = Modifier.weight(2f),
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }

            Column {
                Text("COLOR", style = MaterialTheme.typography.labelMedium, color = TextMeta)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorSwatch(selected = state.newCrewColor == null, onClick = { viewModel.selectNewCrewColor(null) })
                    CREW_COLOR_PALETTE.forEach { hex ->
                        ColorSwatch(hex = hex, selected = state.newCrewColor == hex, onClick = { viewModel.selectNewCrewColor(hex) })
                    }
                }
                Text(
                    if (state.newCrewColor == null) {
                        "Auto-assigned by the server — tap a swatch to pick one instead."
                    } else {
                        "This color will be used instead of an auto-assigned one."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMeta,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HeaderAccent)
                    .clickable(enabled = !state.newCrewSaving, onClick = viewModel::addCrewMember)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.newCrewSaving) {
                    CircularProgressIndicator(color = OnAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add crew member", style = MaterialTheme.typography.labelLarge, color = OnAccent)
                }
            }
        }
    }
}

/** `hex == null` is the "Auto" swatch — a neutral chip rather than a color, since
 * there's no color to preview until the server assigns one. */
@Composable
private fun ColorSwatch(hex: String? = null, selected: Boolean, onClick: () -> Unit) {
    val size = if (selected) 34.dp else 30.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(hex?.let { crewColor(it) } ?: ChipBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = if (hex == null) "Auto-assigned" else "Selected",
                tint = if (hex == null) TextSecondary else OnAccent,
                modifier = Modifier.size(14.dp),
            )
        } else if (hex == null) {
            Text("?", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

/** A crew member's own color as a small circle avatar with a two-letter
 * initial — the same visual anchor used everywhere else this crew member
 * shows up, so it's recognizable at a glance before you've even read the name. */
@Composable
private fun CrewAvatar(member: CrewMember) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(crewColor(member.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            member.name.take(2).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = OnAccent,
        )
    }
}

/** One row per configured crew member — either its summary (avatar, port,
 * default badge, actions) or, while `state.editingCrewName == member.name`,
 * its edit form. The left border strip is that crew member's own color, same
 * as the avatar — two reinforcing cues for "who is this." */
@Composable
private fun CrewRow(member: CrewMember, state: SettingsUiState, viewModel: SettingsViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(crewColor(member.color)))
        Column(Modifier.weight(1f).padding(12.dp)) {
            if (state.editingCrewName == member.name) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMeta,
                        modifier = Modifier.clickable(onClick = viewModel::cancelEditingCrewMember),
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
                        .clickable(enabled = !state.editSaving, onClick = viewModel::saveEditedCrewMember)
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
                    CrewAvatar(member)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(member.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            if (member.isDefault) {
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(ChipBackground).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                    Text("default", style = MaterialTheme.typography.labelSmall, color = HeaderAccent)
                                }
                            }
                        }
                        Text("port ${member.port}", style = MaterialTheme.typography.bodySmall, color = TextMeta)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!member.isDefault) {
                        Text(
                            "Make default",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.clickable { viewModel.setDefaultCrewMember(member.name) },
                        )
                    }
                    Text(
                        "Edit",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.clickable { viewModel.startEditingCrewMember(member) },
                    )
                    Text(
                        "Remove",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeverityUrgentDot,
                        modifier = Modifier.clickable { viewModel.removeCrewMember(member.name) },
                    )
                }
            }
        }
    }
}
