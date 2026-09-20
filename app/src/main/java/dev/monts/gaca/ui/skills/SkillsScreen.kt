package dev.monts.gaca.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.Skill
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.ui.theme.crewChipTint
import dev.monts.gaca.ui.theme.crewColor

@Composable
fun SkillsScreen(
    viewModel: SkillsViewModel,
    onOpenSettings: () -> Unit,
    onOpenThread: (sessionId: String, prefill: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val selectedMember = state.crew.firstOrNull { it.name == state.selectedCrew }
    val dotColor = selectedMember?.let { crewColor(it.color) } ?: HeaderAccent

    LaunchedEffect(state.openChatSessionId) {
        val sessionId = state.openChatSessionId
        if (sessionId != null) {
            onOpenThread(sessionId, state.openChatPrefill)
            viewModel.consumeOpenChat() // otherwise coming back here re-fires this every time
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("Skills", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent)
                Text("What each crew member can actually do", style = MaterialTheme.typography.bodyMedium, color = TextMeta)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextSecondary)
            }
        }

        val errorText = state.error
        if (errorText != null) {
            Text(
                errorText,
                style = MaterialTheme.typography.bodySmall,
                color = SeverityUrgentDot,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }

        when {
            state.crewLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HeaderAccent)
            }
            state.crew.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No crew configured yet — set one up in Settings first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMeta,
                    modifier = Modifier.padding(40.dp),
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.crew.forEach { member ->
                        CrewSelectorPill(member, member.name == state.selectedCrew) { viewModel.selectCrew(member.name) }
                    }
                }

                if (state.skills.isNotEmpty() || state.searchQuery.isNotEmpty()) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
                        placeholder = { Text("Search skills…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMeta) },
                        singleLine = true,
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val filteredSkills = state.skills.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
                    when {
                        state.skillsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        state.skills.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "${state.selectedCrew} has no skills installed right now.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMeta,
                                modifier = Modifier.padding(40.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                        filteredSkills.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No skills match \"${state.searchQuery}\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMeta,
                                modifier = Modifier.padding(40.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                        else -> {
                            // Server already sorts by category then name (api-contract.md §8) —
                            // groupBy preserves that encounter order, so no re-sorting needed here.
                            val byCategory = filteredSkills.groupBy { it.category }
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                byCategory.forEach { (category, skills) ->
                                    item(key = "header-${category ?: "other"}") {
                                        Text(
                                            (category ?: "other").uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextMeta,
                                            modifier = Modifier.padding(bottom = 8.dp),
                                        )
                                    }
                                    items(skills, key = { it.name }) { skill ->
                                        SkillCard(
                                            skill = skill,
                                            dotColor = dotColor,
                                            starting = state.startingChatFor == skill.name,
                                            enabled = state.startingChatFor == null,
                                            onClick = { viewModel.openSkillChat(skill) },
                                            modifier = Modifier.padding(bottom = 8.dp),
                                        )
                                    }
                                }
                                item { Spacer(Modifier.height(4.dp)) }
                                item {
                                    Text(
                                        "Skills live in Hermes — GACA just shows what's there, live from that crew member's own Hermes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMeta,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrewSelectorPill(member: CrewMember, selected: Boolean, onClick: () -> Unit) {
    val color = crewColor(member.color)
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) color else crewChipTint(member.color))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(16.dp).clip(CircleShape).background(if (selected) OnAccent else color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                member.name.take(2).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = if (selected) color else OnAccent,
            )
        }
        Text(member.name, style = MaterialTheme.typography.labelMedium, color = if (selected) OnAccent else color)
    }
}

/** Tapping a skill starts a new chat with it pre-filled — [starting] shows a small
 * spinner in place of the color dot while that's in flight, for just this card. */
@Composable
private fun SkillCard(
    skill: Skill,
    dotColor: Color,
    starting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ChipBackground)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (starting) {
            CircularProgressIndicator(
                color = dotColor,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.TopEnd).size(14.dp),
            )
        } else {
            Box(Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape).background(dotColor))
        }
        Column(Modifier.padding(end = 16.dp)) {
            Text(skill.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(skill.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
