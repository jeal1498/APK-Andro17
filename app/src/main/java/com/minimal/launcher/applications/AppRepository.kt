package com.minimal.launcher.applications

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

/**
 * Source of truth for installed launchable apps. Uses [LauncherApps] (works across user profiles)
 * and its callback for incremental updates on install / uninstall / update. No polling, no services.
 */
class AppRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val activityInfos = ConcurrentHashMap<String, LauncherActivityInfo>()
    private val mutex = Mutex()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Emits packages removed for good, so dependent data (home items, assignments) can be cleaned. */
    private val _removedPackages = MutableSharedFlow<Pair<String, UserHandle>>(extraBufferCapacity = 16)
    val removedPackages: SharedFlow<Pair<String, UserHandle>> = _removedPackages.asSharedFlow()

    /** Emits a package whose icon/label changed so caches can be invalidated. */
    private val _changedPackages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val changedPackages: SharedFlow<String> = _changedPackages.asSharedFlow()

    private val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refreshPackage(packageName, user)
        override fun onPackageChanged(packageName: String, user: UserHandle) {
            _changedPackages.tryEmit(packageName)
            refreshPackage(packageName, user)
        }
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refreshPackage(packageName, user)
            _removedPackages.tryEmit(packageName to user)
        }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach { refreshPackage(it, user) }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach { refreshPackage(it, user) }
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) = Unit
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        scope.launch(Dispatchers.IO) { reloadAll() }
    }

    fun profiles(): List<UserHandle> = runCatching {
        if (Build.VERSION.SDK_INT >= 28) launcherApps.profiles else userManager.userProfiles
    }.getOrDefault(listOf(Process.myUserHandle()))

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    fun userForSerial(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    suspend fun reloadAll() = mutex.withLock {
        val result = ArrayList<AppInfo>()
        activityInfos.clear()
        for (user in profiles()) {
            val list = runCatching { launcherApps.getActivityList(null, user) }
                .onFailure { Log.w(TAG, "Cannot list activities for $user", it) }
                .getOrDefault(emptyList())
            list.filter { it.componentName.packageName != context.packageName }
                .forEach { info -> result += toAppInfo(info, user) }
        }
        publish(result)
        _loaded.value = true
    }

    private fun refreshPackage(packageName: String, user: UserHandle) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val serial = serialOf(user)
                val keep = _apps.value.filterNot { it.packageName == packageName && it.userSerial == serial }
                activityInfos.keys.removeAll { AppInfo.packageOfKey(it) == packageName && it.endsWith("#$serial") }
                val fresh = runCatching { launcherApps.getActivityList(packageName, user) }.getOrDefault(emptyList())
                    .filter { it.componentName.packageName != context.packageName }
                    .map { toAppInfo(it, user) }
                publish(keep + fresh)
            }
        }
    }

    private fun toAppInfo(info: LauncherActivityInfo, user: UserHandle): AppInfo {
        val serial = serialOf(user)
        val key = AppInfo.keyOf(info.componentName, serial)
        activityInfos[key] = info
        val appInfo = info.applicationInfo
        return AppInfo(
            key = key,
            label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
            component = info.componentName,
            user = user,
            userSerial = serial,
            systemCategory = appInfo.category,
            firstInstallTime = info.firstInstallTime,
            isWorkProfile = user != Process.myUserHandle(),
        )
    }

    private fun publish(list: List<AppInfo>) {
        _apps.value = list.distinctBy { it.key }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun activityInfo(key: String): LauncherActivityInfo? = activityInfos[key]

    fun find(key: String): AppInfo? = _apps.value.firstOrNull { it.key == key }

    /** Finds the launcher entry of a package, e.g. to resolve default apps for the dock. */
    fun findByPackage(packageName: String, userSerial: Long? = null): AppInfo? =
        _apps.value.firstOrNull { it.packageName == packageName && (userSerial == null || it.userSerial == userSerial) }

    fun findByComponent(component: ComponentName, user: UserHandle): AppInfo? =
        find(AppInfo.keyOf(component, serialOf(user)))

    companion object {
        private const val TAG = "AppRepository"
    }
}
