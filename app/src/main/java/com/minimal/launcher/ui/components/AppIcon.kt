package com.minimal.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original app icon from the system (adaptive icon rendered with the device mask). */
@Composable
fun AppIconImage(appKey: String, size: Dp, modifier: Modifier = Modifier) {
    val cache = LocalIconCache.current
    var bitmap by remember(appKey) { mutableStateOf(cache.peek(appKey)) }
    LaunchedEffect(appKey) { if (bitmap == null) bitmap = cache.appIcon(appKey) }
    IconBitmap(bitmap, size, modifier)
}

@Composable
fun IconBitmap(bitmap: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    val b = bitmap
    if (b != null) {
        Image(painter = remember(b) { BitmapPainter(b) }, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .padding(size * 0.06f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        )
    }
}

/** Label style for icons drawn on top of the wallpaper (shadow keeps it legible on any image). */
fun wallpaperLabelStyle(darkText: Boolean): TextStyle = TextStyle(
    color = if (darkText) Color(0xFF1B1B1F) else Color.White,
    fontSize = 12.sp,
    lineHeight = 14.sp,
    shadow = if (darkText) null else Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 6f),
)

/** Icon + optional label, used by every grid in the launcher. */
@Composable
fun AppTile(
    icon: @Composable (Dp) -> Unit,
    label: String,
    iconSize: Dp,
    showLabel: Boolean,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            icon(iconSize)
            if (badge != null) Box(Modifier.align(Alignment.BottomEnd)) { badge() }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }
}
