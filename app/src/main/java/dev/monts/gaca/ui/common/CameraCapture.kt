package dev.monts.gaca.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Returns a function that opens the camera app and hands the resulting photo to [onPhoto].
 * The camera app writes into our cache through a FileProvider, so no CAMERA permission is needed.
 */
@Composable
fun rememberTakePhoto(onPhoto: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        pending?.takeIf { saved }?.let(onPhoto)
        pending = null
    }
    return {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(dir, "${UUID.randomUUID()}.jpg"))
        pending = uri
        launcher.launch(uri)
    }
}
