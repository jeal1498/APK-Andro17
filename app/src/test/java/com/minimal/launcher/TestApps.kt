package com.minimal.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.os.Process
import android.os.UserHandle
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Test-only helper: registers launchable activities in Robolectric's LauncherApps / PackageManager shadows. */
object TestApps {
    val sample = listOf(
        "com.whatsapp" to "WhatsApp",
        "com.instagram.android" to "Instagram",
        "com.google.android.apps.maps" to "Maps",
        "com.spotify.music" to "Spotify",
        "com.android.settings" to "Ajustes",
        "com.zorblax.app" to "Zorblax",
    )

    fun install(context: Context, packageName: String, label: String, category: Int = -1, user: UserHandle = Process.myUserHandle()): LauncherActivityInfo {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.category = category
            this.nonLocalizedLabel = label
            this.flags = ApplicationInfo.FLAG_INSTALLED
            this.enabled = true
        }
        val activity = ActivityInfo().apply {
            this.packageName = packageName
            this.name = "$packageName.MainActivity"
            this.nonLocalizedLabel = label
            this.applicationInfo = appInfo
            this.enabled = true
            this.exported = true
        }
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply { this.packageName = packageName; this.applicationInfo = appInfo; this.activities = arrayOf(activity) },
        )
        // SDK 36 framework: LauncherActivityInfo(Context, LauncherActivityInfoInternal) is hidden.
        val internalClass = Class.forName("android.content.pm.LauncherActivityInfoInternal")
        val statesClass = Class.forName("android.content.pm.IncrementalStatesInfo")
        val internal = ReflectionHelpers.callConstructor(
            internalClass,
            ClassParameter.from(ActivityInfo::class.java, activity),
            ClassParameter(statesClass, null),
            ClassParameter.from(UserHandle::class.java, user),
            ClassParameter.from(Boolean::class.javaPrimitiveType, false),
        )
        val info = ReflectionHelpers.callConstructor(
            LauncherActivityInfo::class.java,
            ClassParameter.from(Context::class.java, context),
            ClassParameter(internalClass, internal),
        )
        shadowOf(context.getSystemService(LauncherApps::class.java)).addActivity(user, info)
        return info
    }

    fun installSample(context: Context) = sample.forEach { (pkg, label) -> install(context, pkg, label) }

    fun component(pkg: String) = ComponentName(pkg, "$pkg.MainActivity")
}
