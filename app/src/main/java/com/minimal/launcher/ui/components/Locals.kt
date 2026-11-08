package com.minimal.launcher.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.minimal.launcher.applications.AppActions
import com.minimal.launcher.applications.IconCache

val LocalIconCache = staticCompositionLocalOf<IconCache> { error("IconCache not provided") }
val LocalAppActions = staticCompositionLocalOf<AppActions> { error("AppActions not provided") }
