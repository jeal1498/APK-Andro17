package com.minimal.launcher

import android.content.Context
import android.os.Build

object BuildConfigInfo {
    fun versionName(context: Context): String = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, 0)
        info.versionName ?: "?"
    }.getOrDefault("?")
}
