package dev.monts.gaca.ui.artifact

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.monts.gaca.ui.common.rememberTakePhoto
import kotlinx.coroutines.CompletableDeferred

/** The most photos a page can get from one "Choose photos". */
private const val MAX_PICK = 10

/**
 * The app's side of a page's `app.photos.add()`: the bridge calls [pick], which shows [PagePhotoSheet] and waits for
 * the person's choice. The page never sees the camera, the library or a file, only the ids of what was uploaded.
 */
class PagePhotoRequests {
    class Request(val multiple: Boolean) {
        val answer = CompletableDeferred<List<Uri>?>()
        var launched by mutableStateOf(false)
    }

    var request by mutableStateOf<Request?>(null)
        private set

    /** Suspends until the person picks (the photos' uris) or cancels (null). Call on the main thread. */
    suspend fun pick(multiple: Boolean): List<Uri>? {
        val r = Request(multiple)
        request = r
        return try {
            r.answer.await()
        } finally {
            if (request === r) request = null
        }
    }

    fun finish(uris: List<Uri>?) {
        val r = request ?: return
        request = null
        r.answer.complete(uris?.takeIf { it.isNotEmpty() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagePhotoSheet(requests: PagePhotoRequests) {
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        requests.finish(uri?.let(::listOf))
    }
    val several = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        requests.finish(uris)
    }
    val takePhoto = rememberTakePhoto(onCancel = { requests.finish(null) }) { uri -> requests.finish(listOf(uri)) }

    val request = requests.request
    if (request == null || request.launched) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { requests.finish(null) }, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text("Take photo") },
                leadingContent = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                colors = ListItemDefaults.colors(),
                modifier = Modifier.clickable {
                    request.launched = true
                    takePhoto()
                },
            )
            ListItem(
                headlineContent = { Text(if (request.multiple) "Choose photos" else "Choose photo") },
                leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                modifier = Modifier.clickable {
                    request.launched = true
                    val images = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    if (request.multiple) several.launch(images) else single.launch(images)
                },
            )
        }
    }
}

/** Remembered across recompositions of the page screen. */
@Composable
fun rememberPagePhotoRequests(): PagePhotoRequests = remember { PagePhotoRequests() }
