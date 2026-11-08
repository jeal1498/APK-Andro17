package com.minimal.launcher.applications

import android.content.ComponentName
import android.os.UserHandle

/** A launchable activity of an installed app, for a given user profile. */
data class AppInfo(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val userSerial: Long,
    val systemCategory: Int,
    val firstInstallTime: Long,
    val isWorkProfile: Boolean,
) {
    val packageName: String get() = component.packageName

    companion object {
        fun keyOf(component: ComponentName, userSerial: Long): String =
            "${component.flattenToShortString()}#$userSerial"

        fun packageOfKey(key: String): String = key.substringBefore('/')
    }
}
