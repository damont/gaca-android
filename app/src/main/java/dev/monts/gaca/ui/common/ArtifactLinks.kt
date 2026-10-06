package dev.monts.gaca.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.SuggestBackground

/** What a tappable artifact link needs from the rest of the app: how to open one, and how to
 * learn its title. Provided once by the nav host so chat bubbles and alert screens don't each
 * need it threaded through. */
class ArtifactLinkEnv(
    val open: (artifactId: String) -> Unit,
    val titleOf: suspend (artifactId: String) -> String?,
)

val LocalArtifactLinks = staticCompositionLocalOf<ArtifactLinkEnv?> { null }

/** A small tappable "open this page" chip. [label] is shown until (or instead of) the page's own title. */
@Composable
fun ArtifactChip(artifactId: String, label: String? = null, modifier: Modifier = Modifier) {
    val env = LocalArtifactLinks.current
    val title by produceState(label, artifactId) { value = env?.titleOf?.invoke(artifactId) ?: label }
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SuggestBackground)
            .clickable(enabled = env != null) { env?.open?.invoke(artifactId) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Dashboard, contentDescription = null, tint = HeaderAccent, modifier = Modifier.size(16.dp))
        Text(
            title ?: "Open page",
            style = MaterialTheme.typography.labelLarge,
            color = HeaderAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
