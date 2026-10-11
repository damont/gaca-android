package dev.monts.gaca.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/** One of the Gaca poses (`ic_gaca_*`), scaled to [width]. Pale body in dark theme comes from `drawable-night`. */
@Composable
fun BrandMark(@DrawableRes pose: Int, description: String?, width: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(pose),
        contentDescription = description,
        contentScale = ContentScale.FillWidth,
        modifier = modifier.width(width),
    )
}
