package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val MalachiteShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(2.dp),
    extraLarge = RoundedCornerShape(2.dp)
)

private val DarkColorScheme =
  darkColorScheme(
    primary = MalachiteLightGold,
    onPrimary = MalachiteDarkGreen,
    secondary = MalachiteLightGreen,
    onSecondary = MalachiteDarkGreen,
    tertiary = MalachiteLightGreen,
    background = MalachiteDarkGreen,
    surface = MalachiteSurfaceGreen,
    onBackground = MalachiteOnSurfaceGold,
    onSurface = MalachiteOnSurfaceGold,
    surfaceVariant = MalachiteDarkGreen,
    onSurfaceVariant = MalachiteLightGold,
    outline = MalachiteLightGreen
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Malachite Dark
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(
      colorScheme = colorScheme, 
      typography = Typography, 
      shapes = MalachiteShapes,
      content = content
  )
}
