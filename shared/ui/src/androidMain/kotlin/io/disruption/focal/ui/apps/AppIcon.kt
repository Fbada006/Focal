package io.disruption.focal.ui.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        icon = withContext(Dispatchers.IO) { loadIcon(context, packageName) }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(40.dp))
    } else {
        Box(modifier.size(40.dp))
    }
}

private fun loadIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
}.getOrNull()

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val size = 96
    val bitmap = Bitmap.createBitmap(
        intrinsicWidth.takeIf { it > 0 } ?: size,
        intrinsicHeight.takeIf { it > 0 } ?: size,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
