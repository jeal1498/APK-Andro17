package com.minimal.launcher

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.categories.CategoryResolver
import com.minimal.launcher.categories.CategoryWithApps
import com.minimal.launcher.categories.AppPair
import com.minimal.launcher.pairs.SplitScreenLauncher
import com.minimal.launcher.categories.Resolution
import com.minimal.launcher.data.db.HiddenAppEntity
import com.minimal.launcher.data.db.HomeItemEntity
import com.minimal.launcher.data.prefs.AppSort
import com.minimal.launcher.data.prefs.DrawerTab
import com.minimal.launcher.data.prefs.LauncherSettings
import com.minimal.launcher.home.HomeRepository
import com.minimal.launcher.ui.DeviceProfile
import com.minimal.launcher.usage.FrequentApps
import com.minimal.launcher.data.prefs.FrequentPosition
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val container = application.container
    private val home = container.home

    val settings: StateFlow<LauncherSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hidden: StateFlow<Set<String>> = container.hiddenApps.observe().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val allApps: StateFlow<List<AppInfo>> = container.apps.apps
    val appsLoaded: StateFlow<Boolean> = container.apps.loaded

    /** Apps shown in the drawer and search: not hidden, sorted per settings. */
    val visibleApps: StateFlow<List<AppInfo>> = combine(allApps, hidden, container.settings.settings) { apps, hiddenSet, s ->
        sort(apps.filter { it.key !in hiddenSet }, s.appSort)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categories: StateFlow<List<CategoryWithApps>> = combine(
        visibleApps, container.categories.categories, container.categories.assignments, container.categories.manualApps, container.pairs.pairs,
    ) { apps, cats, assignments, manual, pairs ->
        CategoryResolver.group(apps, cats, assignments, manual.toSet(), pairs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every visible pair, whatever its category. */
    val pairs: StateFlow<List<AppPair>> = categories.map { cats -> cats.flatMap { it.pairs } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _accessibilityRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emitted the first time a feature needs the accessibility service: the UI shows the guided dialog. */
    val accessibilityRequest: SharedFlow<Unit> = _accessibilityRequest.asSharedFlow()

    /** "Frecuentes": pinned apps first, then the most opened ones (decayed by days). Hidden apps never appear. */
    val frequentApps: StateFlow<List<AppInfo>> = combine(visibleApps, container.usage.usage, container.settings.settings) { apps, usage, s ->
        val byKey = apps.associateBy { it.key }
        FrequentApps.select(usage, s.pinnedFrequent, byKey.keys, s.frequentCount, System.currentTimeMillis()).mapNotNull { byKey[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val minuteTicker = kotlinx.coroutines.flow.flow {
        while (true) { emit(System.currentTimeMillis()); kotlinx.coroutines.delay(60_000) }
    }

    /** "Sugeridas" for this time slot / day type and the current context (headphones, car). */
    val suggestedApps: StateFlow<List<AppInfo>> = combine(
        combine(visibleApps, categories) { apps, cats -> apps to cats },
        container.usage.usage,
        container.settings.settings,
        container.deviceContext.state,
        combine(minuteTicker, frequentApps) { now, freq -> now to freq },
    ) { (apps, cats), usage, s, ctx, (now, frequent) ->
        if (!s.showSuggestions) return@combine emptyList()
        val byKey = apps.associateBy { it.key }
        val driving = cats.firstOrNull { it.builtinKey == com.minimal.launcher.categories.BuiltinCategory.DRIVING.key }?.apps.orEmpty().mapTo(HashSet()) { it.key }
        val (slot, day) = com.minimal.launcher.usage.TimeSlots.of(now)
        com.minimal.launcher.usage.Suggestions.select(
            usage, now, slot, day,
            available = byKey.keys,
            dismissed = s.dismissedSuggestions,
            exclude = frequent.mapTo(HashSet()) { it.key },
            context = ctx,
            musicApps = apps.filter(com.minimal.launcher.usage.Suggestions::isMusicApp).map { it.key },
            carApps = apps.filter { com.minimal.launcher.usage.Suggestions.isCarApp(it, driving) }.map { it.key },
        ).mapNotNull { byKey[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Long-press → "No sugerir en este horario". */
    fun dismissSuggestion(appKey: String) = viewModelScope.launch {
        val (slot, day) = com.minimal.launcher.usage.TimeSlots.of(System.currentTimeMillis())
        container.settings.dismissSuggestion(com.minimal.launcher.usage.Suggestions.dismissKey(appKey, slot, com.minimal.launcher.usage.TimeSlots.isWeekend(day)))
        _messages.tryEmit("No se sugerirá en este horario")
    }

    val homeItems: StateFlow<List<HomeItemEntity>?> = home.items
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homePressed: SharedFlow<Unit> = _homePressed.asSharedFlow()

    fun onHomePressed() { _homePressed.tryEmit(Unit) }

    init {
        viewModelScope.launch { initializeDefaults() }
    }

    private fun sort(apps: List<AppInfo>, sort: AppSort) = when (sort) {
        AppSort.ALPHABETICAL -> apps
        AppSort.ALPHABETICAL_DESC -> apps.asReversed()
        AppSort.RECENTLY_INSTALLED -> apps.sortedByDescending { it.firstInstallTime }
    }

    /** First launch: grid sized for this device + a minimal default layout made of installed apps only. */
    private suspend fun initializeDefaults() {
        val s = container.settings.settings.first()
        val config = getApplication<Application>().resources.configuration
        if (s.homeColumns == 0 || s.homeRows == 0) {
            val (cols, rows) = DeviceProfile.defaultHomeGrid(config.smallestScreenWidthDp, maxOf(config.screenHeightDp, config.screenWidthDp))
            container.settings.setHomeGrid(cols, rows)
        }
        if (!s.defaultsCreated) {
            appsLoaded.first { it }
            if (home.count() == 0) createDefaultLayout()
            container.settings.setDefaultsCreated()
        }
        // Release widget ids that were allocated but never placed (e.g. process killed mid-configuration).
        container.widgets.cleanupOrphans(home.widgetIds())
    }

    private suspend fun createDefaultLayout() {
        val s = container.settings.current()
        val cols = s.homeColumns
        home.insertRaw(
            HomeItemEntity(container = HomeItemEntity.CONTAINER_DESKTOP, containerId = 0, x = 0, y = 0, spanX = cols, spanY = 1, type = HomeItemEntity.TYPE_CLOCK),
        )
        val pm = getApplication<Application>().packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS),
        )
        val dockApps = intents.mapNotNull { intent ->
            val pkg = runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }.getOrNull()
            pkg?.takeIf { it != "android" }?.let { container.apps.findByPackage(it) }
        }.distinctBy { it.packageName }.take(cols)
        dockApps.forEachIndexed { i, app ->
            home.insertRaw(HomeItemEntity(container = HomeItemEntity.CONTAINER_DOCK, x = i, type = HomeItemEntity.TYPE_APP, appKey = app.key))
        }
    }

    private fun message(result: HomeRepository.Result, okText: String) {
        when (result) {
            is HomeRepository.Result.Placed -> _messages.tryEmit(okText)
            HomeRepository.Result.NoSpace -> _messages.tryEmit("No hay espacio disponible")
            HomeRepository.Result.AlreadyThere -> _messages.tryEmit("Ya está en la pantalla de inicio")
        }
    }

    // ---- Apps --------------------------------------------------------------------------------

    fun launch(app: AppInfo) = container.actions.launch(app)

    fun findApp(key: String?): AppInfo? = key?.let { container.apps.find(it) }

    fun addToHome(appKey: String, page: Int) = viewModelScope.launch {
        message(home.addAppToDesktop(appKey, page), "Añadida a la pantalla de inicio")
    }

    fun addToDock(appKey: String) = viewModelScope.launch {
        val cols = container.settings.current().homeColumns
        message(home.addToDock(appKey, cols), "Añadida al dock")
    }

    fun setFrequentPinned(appKey: String, pinned: Boolean) = viewModelScope.launch { container.settings.setFrequentPinned(appKey, pinned) }
    fun setFrequentPosition(position: FrequentPosition) = viewModelScope.launch { container.settings.setFrequentPosition(position) }

    // ---- Split-screen pairs ------------------------------------------------------------------

    fun launchPair(pair: AppPair) {
        if (container.splitScreen.launch(pair) == SplitScreenLauncher.Result.NEEDS_PERMISSION) requestAccessibility()
    }

    /** Guided dialog once; afterwards a short reminder. */
    fun requestAccessibility() = viewModelScope.launch {
        if (!container.settings.current().accessibilityPrompted) _accessibilityRequest.tryEmit(Unit)
        else _messages.tryEmit("Activa «Minimal Launcher» en Ajustes › Accesibilidad para usar esta función")
    }

    fun markAccessibilityPrompted() = viewModelScope.launch { container.settings.setAccessibilityPrompted() }

    fun createPair(keys: List<String>) = viewModelScope.launch {
        val id = container.pairs.create(keys)
        _messages.tryEmit(if (id != null) "Par creado en la categoría de ${findApp(keys.first())?.label ?: "la primera app"}" else "Elige 1 o 2 apps más")
        if (id != null && !com.minimal.launcher.pairs.LauncherAccessibilityService.isEnabled(getApplication())) requestAccessibility()
    }

    fun deletePair(id: Long) = viewModelScope.launch { container.pairs.delete(id) }
    fun setPairCategory(id: Long, categoryId: Long?) = viewModelScope.launch { container.pairs.setCategory(id, categoryId) }

    // ---- Universal search --------------------------------------------------------------------

    fun runSearchItem(item: com.minimal.launcher.search.SearchItem) {
        if (item is com.minimal.launcher.search.SearchItem.QuickAction && item.kind == com.minimal.launcher.search.SearchItem.QuickAction.Kind.FLASHLIGHT) {
            container.systemActions.toggleFlashlight(); return
        }
        val intent = com.minimal.launcher.search.UniversalSearch.intentFor(item) ?: return
        val pm = getApplication<Application>().packageManager
        val web = item is com.minimal.launcher.search.SearchItem.QuickAction && item.kind == com.minimal.launcher.search.SearchItem.QuickAction.Kind.WEB
        if (web && intent.resolveActivity(pm) == null) container.actions.startIntent(com.minimal.launcher.search.UniversalSearch.webFallback((item as com.minimal.launcher.search.SearchItem.QuickAction).value))
        else container.actions.startIntent(intent)
    }

    fun dismissContactsSearch() = viewModelScope.launch { container.settings.setContactsSearchDismissed() }

    fun hideApp(appKey: String) = viewModelScope.launch { container.hiddenApps.hide(HiddenAppEntity(appKey)) }
    fun unhideApp(appKey: String) = viewModelScope.launch { container.hiddenApps.unhide(appKey) }

    // ---- Home items --------------------------------------------------------------------------

    fun removeItem(id: Long) = viewModelScope.launch {
        home.delete(id)?.let { container.widgets.deleteId(it) }
    }

    fun moveToDesktop(id: Long, page: Int, x: Int, y: Int) = viewModelScope.launch {
        ensurePage(page)
        if (!home.moveToDesktop(id, page, x, y)) _messages.tryEmit("No cabe en esa posición")
    }

    fun addAppAt(appKey: String, page: Int, x: Int, y: Int) = viewModelScope.launch {
        ensurePage(page)
        val r = home.addAt(HomeItemEntity(container = HomeItemEntity.CONTAINER_DESKTOP, type = HomeItemEntity.TYPE_APP, appKey = appKey), page, x, y)
        if (r !is HomeRepository.Result.Placed) _messages.tryEmit("No cabe en esa posición")
    }

    private suspend fun ensurePage(page: Int) {
        val pages = container.settings.current().homePages
        if (page >= pages) container.settings.setHomePages(page + 1)
    }

    fun moveToDock(id: Long, slot: Int) = viewModelScope.launch {
        val cols = container.settings.current().homeColumns
        if (!home.moveToDock(id, slot, cols)) _messages.tryEmit("Ese espacio del dock está ocupado")
    }

    fun addAppToDockSlot(appKey: String, slot: Int) = viewModelScope.launch {
        val cols = container.settings.current().homeColumns
        val r = home.addToDock(appKey, cols, slot)
        if (r !is HomeRepository.Result.Placed) message(r, "")
    }

    fun createFolder(targetId: Long, draggedId: Long) = viewModelScope.launch { home.createFolder(targetId, draggedId) }
    fun createFolderWithApp(targetId: Long, appKey: String) = viewModelScope.launch { home.createFolderWithApp(appKey, targetId) }
    fun addToFolder(folderId: Long, itemId: Long) = viewModelScope.launch { home.addToFolder(folderId, itemId) }
    fun addAppToFolder(folderId: Long, appKey: String) = viewModelScope.launch { home.addAppToFolder(folderId, appKey) }
    fun renameFolder(folderId: Long, title: String) = viewModelScope.launch { home.renameFolder(folderId, title) }
    fun removeFromFolder(itemId: Long, page: Int) = viewModelScope.launch { home.removeFromFolder(itemId, page) }
    fun ungroupFolder(folderId: Long, page: Int) = viewModelScope.launch { home.ungroupFolder(folderId, page) }

    fun resizeItem(id: Long, x: Int, y: Int, spanX: Int, spanY: Int) = viewModelScope.launch {
        if (!home.resize(id, x, y, spanX, spanY)) _messages.tryEmit("No hay espacio para ese tamaño")
    }

    fun addPage() = viewModelScope.launch {
        val s = container.settings.current()
        container.settings.setHomePages(s.homePages + 1)
    }

    fun removePage(page: Int) = viewModelScope.launch {
        if (!home.removePage(page)) _messages.tryEmit("Solo se pueden eliminar páginas vacías")
    }

    fun addClock(page: Int) = viewModelScope.launch {
        val cols = container.settings.current().homeColumns
        message(
            home.addToDesktop(HomeItemEntity(container = 0, type = HomeItemEntity.TYPE_CLOCK, spanX = cols.coerceAtMost(4).coerceAtLeast(minOf(cols, 4)), spanY = 1), page),
            "Reloj añadido",
        )
    }

    fun addShortcut(packageName: String, shortcutId: String, title: String, userSerial: Long, page: Int) = viewModelScope.launch {
        val user = container.apps.userForSerial(userSerial) ?: return@launch
        container.actions.pinShortcut(packageName, shortcutId, user, true)
        message(
            home.addToDesktop(
                HomeItemEntity(container = 0, type = HomeItemEntity.TYPE_SHORTCUT, packageName = packageName, shortcutId = shortcutId, title = title, userSerial = userSerial),
                page,
            ),
            "Acceso directo añadido",
        )
    }

    // ---- Widgets -----------------------------------------------------------------------------

    data class PendingWidget(val appWidgetId: Int, val info: AppWidgetProviderInfo, val page: Int, val spanX: Int, val spanY: Int)

    var pendingWidget: PendingWidget? = null

    fun placePendingWidget() = viewModelScope.launch {
        val p = pendingWidget ?: return@launch
        pendingWidget = null
        val r = home.addToDesktop(
            HomeItemEntity(container = 0, type = HomeItemEntity.TYPE_WIDGET, widgetId = p.appWidgetId, spanX = p.spanX, spanY = p.spanY),
            p.page,
        )
        if (r !is HomeRepository.Result.Placed) container.widgets.deleteId(p.appWidgetId)
        message(r, "Widget añadido")
    }

    fun cancelPendingWidget() {
        pendingWidget?.let { container.widgets.deleteId(it.appWidgetId) }
        pendingWidget = null
    }

    // ---- Categories --------------------------------------------------------------------------

    suspend fun resolutionFor(app: AppInfo): Resolution {
        val cats = container.categories.categories.first()
        val assignments = container.categories.assignments.first()
        val manual = container.categories.manualApps.first().toSet()
        val map = assignments.groupBy({ it.appKey }, { it.categoryId }).mapValues { it.value.toSet() }
        return CategoryResolver.resolve(app, CategoryResolver.targets(cats), manual, map)
    }

    fun setAppCategories(appKey: String, ids: Set<Long>) = viewModelScope.launch {
        container.categories.setManualCategories(appKey, ids)
    }

    /** Adds a manual assignment while keeping the app's current categories. */
    fun addAppsToCategory(categoryId: Long, apps: List<AppInfo>) = viewModelScope.launch {
        apps.forEach { app ->
            val current = resolutionFor(app).categoryIds
            container.categories.setManualCategories(app.key, current + categoryId)
        }
    }

    fun removeAppFromCategory(app: AppInfo, categoryId: Long) = viewModelScope.launch {
        val current = resolutionFor(app).categoryIds
        container.categories.setManualCategories(app.key, current - categoryId)
    }

    fun restoreAutomatic(appKey: String) = viewModelScope.launch { container.categories.restoreAutomatic(appKey) }

    fun createCategory(name: String, iconKey: String, keywords: String) = viewModelScope.launch {
        if (name.isNotBlank()) container.categories.create(name, iconKey, keywords)
    }

    fun updateCategory(id: Long, name: String, iconKey: String, keywords: String) = viewModelScope.launch {
        val existing = container.categories.categories.first().firstOrNull { it.id == id } ?: return@launch
        container.categories.update(existing.copy(name = name.trim().ifEmpty { existing.name }, iconKey = iconKey, keywords = keywords))
    }

    fun deleteCategory(id: Long) = viewModelScope.launch { container.categories.delete(id) }


    fun setDrawerTab(tab: DrawerTab) = viewModelScope.launch { container.settings.setDrawerTab(tab) }

    fun setAppSort(sort: AppSort) = viewModelScope.launch { container.settings.setAppSort(sort) }

    fun shortcutUser(serial: Long) = container.apps.userForSerial(serial)
}
