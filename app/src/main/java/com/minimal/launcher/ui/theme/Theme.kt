package com.minimal.launcher.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.minimal.launcher.data.prefs.ThemeMode

// Fallback palette (pre-Android 12 or dynamic color disabled), Material 3 baseline tones.
private val LightFallback = lightColorScheme(
    primary = Color(0xFF3A5BA9), onPrimary = Color.White, primaryContainer = Color(0xFFDAE2FF),
    onPrimaryContainer = Color(0xFF001848), secondary = Color(0xFF575E71), secondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFF715573), tertiaryContainer = Color(0xFFFCD7FB), surface = Color(0xFFFAF8FF),
    surfaceContainer = Color(0xFFEEEDF4), surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE2E2E9), surfaceContainerLow = Color(0xFFF4F3FA),
)
private val DarkFallback = darkColorScheme(
    primary = Color(0xFFB1C5FF), onPrimary = Color(0xFF002C71), primaryContainer = Color(0xFF1F438F),
    onPrimaryContainer = Color(0xFFDAE2FF), secondary = Color(0xFFC0C6DD), secondaryContainer = Color(0xFF404659),
    tertiary = Color(0xFFDFBBDE), tertiaryContainer = Color(0xFF583E5B), surface = Color(0xFF121318),
    surfaceContainer = Color(0xFF1E1F25), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A), surfaceContainerLow = Color(0xFF1A1B21),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MinimalTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        // Dynamic Color: palette derived from the wallpaper by the system (Material You).
        dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }
    // Default M3 typography uses the platform font (Roboto / device default).
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Whether the current wallpaper prefers dark text (bright wallpaper), as reported by the system.
 * Used for home-screen labels, clock and status bar icons, like the stock launcher.
 */
@Composable
fun rememberWallpaperPrefersDarkText(): Boolean {
    val context = LocalContext.current
    var darkText by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        // WallpaperColors APIs exist from API 27; dark-text hints are public from API 31.
        if (Build.VERSION.SDK_INT < 31) return@DisposableEffect onDispose { }
        val wm = WallpaperManager.getInstance(context)
        fun read(colors: WallpaperColors?) {
            darkText = colors != null && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        }
        runCatching { read(wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)) }
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) read(colors)
        }
        runCatching { wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { wm.removeOnColorsChangedListener(listener) } }
    }
    return darkText
}
