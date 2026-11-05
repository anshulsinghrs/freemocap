package com.physioar.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF00897B)
val TealDark = Color(0xFF4DB6AC)
val Good = Color(0xFF2E7D32)
val Caution = Color(0xFFF9A825)
val Problem = Color(0xFFC62828)
val Neutral = Color(0xFF607D8B)

@Composable
fun PhysioArTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = TealDark, secondary = Color(0xFF90CAF9))
    } else {
        lightColorScheme(primary = Teal, secondary = Color(0xFF1565C0))
    }
    MaterialTheme(colorScheme = colors, content = content)
}
