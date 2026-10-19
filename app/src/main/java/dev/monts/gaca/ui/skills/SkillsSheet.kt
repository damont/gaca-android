package dev.monts.gaca.ui.skills

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monts.gaca.data.repo.Skill
import dev.monts.gaca.ui.theme.BorderColor
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SuggestBackground
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** How many skills the short sheet lists before "See all skills". */
private const val TOP_COUNT = 5

/**
 * The skills sheet behind the composer's sparkle button: the few most used (the list arrives
 * most-used first), a search field, and "See all skills" to open the full list grouped by category.
 * Typing in the search field also switches to that grouped list, filtered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsSheet(skills: List<Skill>, onPick: (Skill) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val searching = query.isNotBlank()
    val anyUsed = skills.any { it.useCount > 0 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = OnAccent) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                if (showAll && !searching) "All skills" else "Skills",
                style = MaterialTheme.typography.titleLarge,
                color = HeaderAccent,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            SearchField(query, onChange = { query = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(min = 120.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                when {
                    searching || showAll -> {
                        val shown = if (searching) matchSkills(skills, query) else skills
                        if (shown.isEmpty()) item { EmptyNote("No skill matches “${query.trim()}”.") }
                        groupedByCategory(shown).forEach { (category, group) ->
                            item(key = "h-$category") { SectionLabel(category) }
                            items(group, key = { "s-${it.name}" }) { SkillRow(it) { onPick(it) } }
                        }
                    }
                    skills.isEmpty() -> item { EmptyNote("No skills to show yet.") }
                    else -> {
                        item { SectionLabel(if (anyUsed) "Most used" else "Skills") }
                        items(skills.take(TOP_COUNT), key = { "t-${it.name}" }) { SkillRow(it) { onPick(it) } }
                    }
                }
            }
            if (!searching && !showAll && skills.size > TOP_COUNT) {
                Button(
                    onClick = { showAll = true; scope.launch { sheetState.expand() } },
                    colors = ButtonDefaults.buttonColors(containerColor = HeaderAccent, contentColor = OnAccent),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp),
                ) { Text("See all skills") }
            }
        }
    }
}

/** The "/" picker: a short card above the composer listing the skills that match what follows the slash. */
@Composable
fun SlashSkillList(skills: List<Skill>, query: String, onPick: (Skill) -> Unit, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    val matches = matchSkills(skills, query).take(4)
    if (matches.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = OnAccent,
        shadowElevation = 6.dp,
        border = BorderStroke(0.5.dp, BorderColor),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            SectionLabel("Skills")
            matches.forEach { SkillRow(it, slash = true) { onPick(it) } }
            Text(
                "See all skills ›",
                style = MaterialTheme.typography.labelLarge,
                color = HeaderAccent,
                modifier = Modifier.clickable(onClick = onSeeAll).heightIn(min = 44.dp).padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SkillRow(skill: Skill, slash: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(SuggestBackground), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = HeaderAccent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (slash) "/${skill.name}" else skill.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (skill.description.isNotBlank()) {
                Text(skill.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
        color = TextSecondary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = TextMeta, modifier = Modifier.padding(20.dp))
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(PageBackground).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = TextMeta, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search skills", style = FIELD, color = TextMeta)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = FIELD.copy(color = TextPrimary),
                cursorBrush = SolidColor(HeaderAccent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val FIELD = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)

/** Category → its skills, in the order the list arrived; skills with no category come last as "Other". */
internal fun groupedByCategory(skills: List<Skill>): List<Pair<String, List<Skill>>> {
    val named = skills.filter { !it.category.isNullOrBlank() }.groupBy { it.category!! }.toList()
    val other = skills.filter { it.category.isNullOrBlank() }
    return if (other.isEmpty()) named else named + ("Other" to other)
}
