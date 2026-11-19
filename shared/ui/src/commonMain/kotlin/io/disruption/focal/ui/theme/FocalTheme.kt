package io.disruption.focal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF3F51B5)
private val IndigoLight = Color(0xFFB4BCF5)

@Composable
fun FocalTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = IndigoLight)
    } else {
        lightColorScheme(primary = Indigo)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
