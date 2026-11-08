package com.minimal.launcher.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.minimal.launcher.gestures.GestureAction
import com.minimal.launcher.gestures.HomeGesture

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppSort { ALPHABETICAL, ALPHABETICAL_DESC, RECENTLY_INSTALLED }
enum class DrawerTab { CATEGORIES, ALL }
enum class FrequentPosition { TOP, BOTTOM, SIDEBAR, HIDDEN }

data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** 0 = not initialised yet (chosen from the device size on first launch). */
    val homeColumns: Int = 0,
    val homeRows: Int = 0,
    val homePages: Int = 1,
    /** 0 = automatic (depends on window size). */
    val drawerColumns: Int = 0,
    val categoryColumns: Int = 0,
    val showHomeLabels: Boolean = true,
    val showDrawerLabels: Boolean = true,
    val appSort: AppSort = AppSort.ALPHABETICAL,
    val drawerTab: DrawerTab = DrawerTab.CATEGORIES,
    val allowRotation: Boolean = true,
    val showSearchBar: Boolean = true,
    val defaultsCreated: Boolean = false,
    /** Learn which apps are opened when (local only) for Frecuentes / Sugeridas. */
    val learnUsage: Boolean = true,
    val frequentPosition: FrequentPosition = FrequentPosition.TOP,
    val frequentCount: Int = 5,
    /** Apps pinned to Frecuentes by hand, in order. */
    val pinnedFrequent: List<String> = emptyList(),
    /** The guided accessibility dialog is shown only once. */
    val accessibilityPrompted: Boolean = false,
    val gestures: Map<HomeGesture, GestureAction> = HomeGesture.entries.associateWith { GestureAction.defaultFor(it) },
) {
    fun gesture(g: HomeGesture): GestureAction = gestures[g] ?: GestureAction.defaultFor(g)
}


class SettingsRepository(private val store: DataStore<Preferences>) {

    /** One instance per process (owned by AppContainer). */
    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
    )

    companion object {
        const val FILE_NAME = "launcher_settings"
    }

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val homeCols = intPreferencesKey("home_columns")
        val homeRows = intPreferencesKey("home_rows")
        val homePages = intPreferencesKey("home_pages")
        val drawerCols = intPreferencesKey("drawer_columns")
        val categoryCols = intPreferencesKey("category_columns")
        val homeLabels = booleanPreferencesKey("home_labels")
        val drawerLabels = booleanPreferencesKey("drawer_labels")
        val sort = stringPreferencesKey("app_sort")
        val tab = stringPreferencesKey("drawer_tab")
        val rotation = booleanPreferencesKey("allow_rotation")
        val searchBar = booleanPreferencesKey("search_bar")
        val defaults = booleanPreferencesKey("defaults_created")
        val taxonomy = intPreferencesKey("category_taxonomy_version")
        val learnUsage = booleanPreferencesKey("learn_usage")
        val frequentPosition = stringPreferencesKey("frequent_position")
        val frequentCount = intPreferencesKey("frequent_count")
        val pinnedFrequent = stringPreferencesKey("pinned_frequent")
        val a11yPrompted = booleanPreferencesKey("accessibility_prompted")
    }

    val settings: Flow<LauncherSettings> = store.data.map { p ->
        LauncherSettings(
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamic] ?: true,
            homeColumns = p[Keys.homeCols] ?: 0,
            homeRows = p[Keys.homeRows] ?: 0,
            homePages = (p[Keys.homePages] ?: 1).coerceAtLeast(1),
            drawerColumns = p[Keys.drawerCols] ?: 0,
            categoryColumns = p[Keys.categoryCols] ?: 0,
            showHomeLabels = p[Keys.homeLabels] ?: true,
            showDrawerLabels = p[Keys.drawerLabels] ?: true,
            appSort = p[Keys.sort]?.let { runCatching { AppSort.valueOf(it) }.getOrNull() } ?: AppSort.ALPHABETICAL,
            drawerTab = p[Keys.tab]?.let { runCatching { DrawerTab.valueOf(it) }.getOrNull() } ?: DrawerTab.CATEGORIES,
            allowRotation = p[Keys.rotation] ?: true,
            showSearchBar = p[Keys.searchBar] ?: true,
            defaultsCreated = p[Keys.defaults] ?: false,
            learnUsage = p[Keys.learnUsage] ?: true,
            frequentPosition = p[Keys.frequentPosition]?.let { runCatching { FrequentPosition.valueOf(it) }.getOrNull() } ?: FrequentPosition.TOP,
            frequentCount = (p[Keys.frequentCount] ?: 5).coerceIn(5, 8),
            accessibilityPrompted = p[Keys.a11yPrompted] ?: false,
            gestures = HomeGesture.entries.associateWith { g -> GestureAction.decode(p[gestureKey(g)]) ?: GestureAction.defaultFor(g) },
            pinnedFrequent = p[Keys.pinnedFrequent].orEmpty().split('\n').filter { it.isNotEmpty() },
        )
    }

    private fun gestureKey(g: HomeGesture) = stringPreferencesKey("gesture_${g.name}")
    suspend fun setGesture(g: HomeGesture, action: GestureAction) = store.edit { it[gestureKey(g)] = action.encode() }

    suspend fun current(): LauncherSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.dynamic] = enabled }
    suspend fun setHomeGrid(columns: Int, rows: Int) = store.edit {
        it[Keys.homeCols] = columns
        it[Keys.homeRows] = rows
    }
    suspend fun setHomePages(pages: Int) = store.edit { it[Keys.homePages] = pages.coerceIn(1, 12) }
    suspend fun setDrawerColumns(columns: Int) = store.edit { it[Keys.drawerCols] = columns }
    suspend fun setCategoryColumns(columns: Int) = store.edit { it[Keys.categoryCols] = columns }
    suspend fun setShowHomeLabels(show: Boolean) = store.edit { it[Keys.homeLabels] = show }
    suspend fun setShowDrawerLabels(show: Boolean) = store.edit { it[Keys.drawerLabels] = show }
    suspend fun setAppSort(sort: AppSort) = store.edit { it[Keys.sort] = sort.name }
    suspend fun setDrawerTab(tab: DrawerTab) = store.edit { it[Keys.tab] = tab.name }
    suspend fun setAllowRotation(allow: Boolean) = store.edit { it[Keys.rotation] = allow }
    suspend fun setShowSearchBar(show: Boolean) = store.edit { it[Keys.searchBar] = show }
    suspend fun setLearnUsage(enabled: Boolean) = store.edit { it[Keys.learnUsage] = enabled }
    suspend fun setFrequentPosition(position: FrequentPosition) = store.edit { it[Keys.frequentPosition] = position.name }
    suspend fun setFrequentCount(count: Int) = store.edit { it[Keys.frequentCount] = count.coerceIn(5, 8) }
    suspend fun setFrequentPinned(appKey: String, pinned: Boolean) = store.edit {
        val current = it[Keys.pinnedFrequent].orEmpty().split('\n').filter { k -> k.isNotEmpty() && k != appKey }
        it[Keys.pinnedFrequent] = (if (pinned) current + appKey else current).joinToString("\n")
    }
    suspend fun setAccessibilityPrompted() = store.edit { it[Keys.a11yPrompted] = true }
    suspend fun setDefaultsCreated() = store.edit { it[Keys.defaults] = true }

    /** Built-in category taxonomy version; 1 = installations created before the versioning existed. */
    suspend fun categoryTaxonomyVersion(): Int = store.data.first()[Keys.taxonomy] ?: 1
    suspend fun setCategoryTaxonomyVersion(version: Int) = store.edit { it[Keys.taxonomy] = version }
}
