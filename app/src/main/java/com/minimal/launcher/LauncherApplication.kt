package com.minimal.launcher

import android.app.Application
import android.content.Context
import com.minimal.launcher.applications.AppActions
import com.minimal.launcher.applications.AppRepository
import com.minimal.launcher.applications.IconCache
import com.minimal.launcher.categories.CategoryRepository
import com.minimal.launcher.categories.TaxonomyVersionStore
import com.minimal.launcher.data.db.HiddenAppDao
import com.minimal.launcher.data.db.LauncherDatabase
import com.minimal.launcher.data.prefs.SettingsRepository
import com.minimal.launcher.home.HomeRepository
import com.minimal.launcher.usage.UsageRepository
import com.minimal.launcher.pairs.PairRepository
import com.minimal.launcher.pairs.SplitScreenLauncher
import com.minimal.launcher.widgets.WidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container: the app is small enough that a DI framework would only add weight. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: LauncherDatabase = LauncherDatabase.create(appContext)
    val settings = SettingsRepository(appContext)
    val apps = AppRepository(appContext, scope)
    val icons = IconCache(appContext, apps)
    val categories = CategoryRepository(
        database.categoryDao(),
        database.assignmentDao(),
        object : TaxonomyVersionStore {
            override suspend fun get() = settings.categoryTaxonomyVersion()
            override suspend fun set(version: Int) { settings.setCategoryTaxonomyVersion(version) }
        },
    )
    val hiddenApps: HiddenAppDao = database.hiddenAppDao()
    val home = HomeRepository(database, settings)
    val usage = UsageRepository(database.usageDao(), scope, enabled = { settings.current().learnUsage })
    val actions = AppActions(appContext, onLaunched = { usage.record(it.key) })
    val pairs = PairRepository(database.pairDao())
    val systemActions by lazy { com.minimal.launcher.gestures.SystemActions(appContext) }
    val splitScreen = SplitScreenLauncher(appContext, actions, scope, onLaunched = { usage.record(it.key) })
    val widgets = WidgetManager(appContext)

    fun start() {
        apps.start()
        scope.launch { categories.ensureDefaults() }
        scope.launch { apps.changedPackages.collect { icons.invalidatePackage(it) } }
        scope.launch {
            apps.removedPackages.collect { (pkg, user) ->
                val serial = apps.serialOf(user)
                // Package fully removed for this user: drop its home items (app entries + pinned shortcuts).
                if (apps.apps.value.none { it.packageName == pkg && it.userSerial == serial }) {
                    val keys = home.allItems().mapNotNull { it.appKey }
                        .filter { it.startsWith("$pkg/") && it.endsWith("#$serial") }
                    home.removeApps(keys, pkg, serial)
                    usage.forget(database.usageDao().getAll().map { it.appKey }.distinct().filter { it.startsWith("$pkg/") && it.endsWith("#$serial") })
                    icons.invalidatePackage(pkg)
                }
            }
        }
    }
}

class LauncherApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

val Context.container: AppContainer get() = (applicationContext as LauncherApplication).container
