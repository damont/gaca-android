package dev.monts.gaca.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monts.gaca.ui.theme.BorderColor
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.util.PhotoPrep
import dev.monts.gaca.util.PreparedPhoto

/** One red line above the composer for a failed send or run. */
@Composable
fun ComposerError(text: String?) {
    if (text == null) return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = SeverityUrgentDot,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
    )
}

/**
 * The floating white capsule shared by the Chat home and the thread: `+` for photos (up to
 * [PhotoPrep.MAX_PHOTOS], shown as thumbnails with a remove badge), a growing text field, and a
 * round send button in the crew's solid color when sendable.
 */
@Composable
fun ChatComposer(
    text: String,
    photos: List<PreparedPhoto>,
    preparingPhotos: Boolean,
    sending: Boolean,
    placeholder: String,
    sendColor: Color,
    onTextChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = (text.isNotBlank() || photos.isNotEmpty()) && !sending && !preparingPhotos
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        shadowElevation = 6.dp,
        border = BorderStroke(0.5.dp, BorderColor),
    ) {
        androidx.compose.foundation.layout.Column {
            if (photos.isNotEmpty() || preparingPhotos) PendingPhotos(photos, preparingPhotos, sendColor, onRemovePhoto)
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onPickPhoto, enabled = photos.size < PhotoPrep.MAX_PHOTOS && !sending) {
                    Icon(Icons.Filled.Add, contentDescription = "Attach photo", tint = TextSecondary)
                }
                Box(Modifier.weight(1f).heightIn(min = 48.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text(placeholder, style = FIELD_STYLE, color = TextMeta, maxLines = 1)
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = FIELD_STYLE.copy(color = TextPrimary),
                        cursorBrush = SolidColor(sendColor),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                }
                Box(
                    Modifier
                        .padding(bottom = 2.dp, end = 2.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend || sending) sendColor else BorderColor)
                        .clickable(enabled = canSend, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    if (sending) {
                        CircularProgressIndicator(color = OnAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = OnAccent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private val FIELD_STYLE = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)

@Composable
private fun PendingPhotos(photos: List<PreparedPhoto>, preparing: Boolean, accent: Color, onRemove: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        photos.forEach { photo ->
            Box(Modifier.size(64.dp)) {
                Image(
                    bitmap = photo.thumbnail.asImageBitmap(),
                    contentDescription = "Attached photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)),
                )
                Box(
                    Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(HeaderAccent).clickable { onRemove(photo.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = OnAccent, modifier = Modifier.size(12.dp))
                }
            }
        }
        if (preparing) CircularProgressIndicator(color = accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}
