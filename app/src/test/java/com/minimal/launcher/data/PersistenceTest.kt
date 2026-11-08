package com.minimal.launcher.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.minimal.launcher.LauncherApplication
import com.minimal.launcher.categories.BuiltinCategory
import com.minimal.launcher.categories.CATEGORY_TAXONOMY_VERSION
import com.minimal.launcher.categories.CategoryRepository
import com.minimal.launcher.categories.TaxonomyVersionStore
import com.minimal.launcher.data.db.CategoryEntity
import com.minimal.launcher.data.db.HomeItemEntity
import com.minimal.launcher.data.db.HomeItemEntity.Companion.CONTAINER_DESKTOP
import com.minimal.launcher.data.db.HomeItemEntity.Companion.CONTAINER_DOCK
import com.minimal.launcher.data.db.HomeItemEntity.Companion.CONTAINER_FOLDER
import com.minimal.launcher.data.db.HomeItemEntity.Companion.TYPE_APP
import com.minimal.launcher.data.db.HomeItemEntity.Companion.TYPE_FOLDER
import com.minimal.launcher.data.db.HomeItemEntity.Companion.TYPE_WIDGET
import com.minimal.launcher.data.db.LauncherDatabase
import com.minimal.launcher.data.prefs.AppSort
import com.minimal.launcher.data.prefs.SettingsRepository
import com.minimal.launcher.data.prefs.ThemeMode
import com.minimal.launcher.home.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Everything must survive closing the app / restarting the device (= reopening DB and DataStore). */
@RunWith(RobolectricTestRunner::class)
class PersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<LauncherApplication>()
    private lateinit var dbName: String
    private lateinit var prefsFile: File
    private var storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun openDb() = Room.databaseBuilder(context, LauncherDatabase::class.java, dbName).allowMainThreadQueries().build()
    private fun openSettings(): SettingsRepository {
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return SettingsRepository(PreferenceDataStoreFactory.create(scope = storeScope) { prefsFile })
    }
    private suspend fun closeSettings() = storeScope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()

    @Before fun setUp() {
        dbName = "test-${System.nanoTime()}.db"
        prefsFile = File(context.filesDir, "test-${System.nanoTime()}.preferences_pb")
    }

    @After fun tearDown() {
        storeScope.cancel()
        context.deleteDatabase(dbName)
        prefsFile.delete()
    }

    @Test fun legacyTaxonomyIsMigratedWithoutLosingManualData() = runBlocking {
        var db = openDb()
        // A v1 installation: old built-ins (one renamed by the user) + a custom category + manual assignments.
        val legacy = listOf(
            "communication" to "Comunicación", "social" to "Social", "work" to "Mi trabajo", "finance" to "Finanzas",
            "entertainment" to "Entretenimiento", "games" to "Juegos", "tools" to "Herramientas", "shopping" to "Compras",
            "navigation" to "Navegación", "photography" to "Fotografía", "education" to "Educación",
        )
        legacy.forEachIndexed { i, (key, name) ->
            db.categoryDao().insert(CategoryEntity(name = name, iconKey = "folder", position = i, builtinKey = key))
        }
        val customId = db.categoryDao().insert(CategoryEntity(name = "Deportes", iconKey = "health", position = 11))
        val gamesId = db.categoryDao().getAll().first { it.builtinKey == "games" }.id
        val settings = openSettings()
        val store = object : TaxonomyVersionStore {
            override suspend fun get() = settings.categoryTaxonomyVersion()
            override suspend fun set(version: Int) { settings.setCategoryTaxonomyVersion(version) }
        }
        var repo = CategoryRepository(db.categoryDao(), db.assignmentDao(), store)
        repo.setManualCategories("com.zorblax/.Main#0", setOf(gamesId, customId))
        assertEquals(1, settings.categoryTaxonomyVersion())

        repo.ensureDefaults()
        repo.ensureDefaults() // runs once
        val cats = db.categoryDao().getAll()
        // The 13 built-ins first, in order, then the retired "Juegos" (kept: it has manual data) and the custom one.
        assertEquals(BuiltinCategory.entries.map { it.key }, cats.take(13).map { it.builtinKey })
        assertEquals(listOf("Juegos", "Deportes"), cats.drop(13).map { it.name })
        assertNull(cats.firstOrNull { it.name == "Fotografía" })
        assertEquals("Mi trabajo", cats.first { it.builtinKey == "work" }.name) // user rename kept
        assertEquals("Redes sociales", cats.first { it.builtinKey == "social" }.name)
        assertEquals("Finanzas personales", cats.first { it.builtinKey == "finance" }.name)
        assertEquals(cats.map { it.id }.distinct().size, cats.size)
        assertEquals(setOf(gamesId, customId), db.assignmentDao().assignmentsFor("com.zorblax/.Main#0").map { it.categoryId }.toSet())
        assertEquals(CATEGORY_TAXONOMY_VERSION, settings.categoryTaxonomyVersion())
        closeSettings()
        db.close()

        // After a restart nothing changes (and a deleted built-in is not resurrected).
        db = openDb()
        val settings2 = openSettings()
        repo = CategoryRepository(db.categoryDao(), db.assignmentDao(), object : TaxonomyVersionStore {
            override suspend fun get() = settings2.categoryTaxonomyVersion()
            override suspend fun set(version: Int) { settings2.setCategoryTaxonomyVersion(version) }
        })
        val google = db.categoryDao().getAll().first { it.builtinKey == "google" }.id
        repo.delete(google)
        repo.ensureDefaults()
        assertEquals(cats.size - 1, db.categoryDao().getAll().size)
        closeSettings()
        db.close()
    }

    @Test fun categoriesAndManualAssignmentsSurviveRestart() = runBlocking {
        var db = openDb()
        var repo = CategoryRepository(db.categoryDao(), db.assignmentDao())
        repo.ensureDefaults()
        repo.ensureDefaults() // idempotent: no duplicates
        val cats = db.categoryDao().getAll()
        assertEquals(BuiltinCategory.entries.size, cats.size)
        val custom = repo.create("Deportes", "health", "futbol, gym")
        val work = cats.first { it.builtinKey == "work" }.id
        repo.setManualCategories("com.whatsapp/.Main#0", setOf(work, custom))
        repo.setManualCategories("com.zorblax/.Main#0", emptySet())
        db.close()

        db = openDb()
        repo = CategoryRepository(db.categoryDao(), db.assignmentDao())
        repo.ensureDefaults()
        assertEquals(BuiltinCategory.entries.size + 1, db.categoryDao().getAll().size)
        assertEquals(setOf(work, custom), db.assignmentDao().assignmentsFor("com.whatsapp/.Main#0").map { it.categoryId }.toSet())
        assertTrue(repo.isManual("com.zorblax/.Main#0"))
        assertEquals("futbol, gym", db.categoryDao().getAll().first { it.id == custom }.keywords)

        // Deleting a category cascades its assignments; restore automatic clears manual state.
        repo.delete(custom)
        assertEquals(setOf(work), db.assignmentDao().assignmentsFor("com.whatsapp/.Main#0").map { it.categoryId }.toSet())
        repo.restoreAutomatic("com.whatsapp/.Main#0")
        assertFalse(repo.isManual("com.whatsapp/.Main#0"))
        repo.restoreAllAutomatic()
        assertFalse(repo.isManual("com.zorblax/.Main#0"))

        // Reorder persists.
        val ids = db.categoryDao().getAll().map { it.id }.reversed()
        repo.reorder(ids)
        assertEquals(ids, db.categoryDao().getAll().map { it.id })
        db.close()
    }

    @Test fun settingsSurviveRestart() = runBlocking {
        var s = openSettings()
        s.setThemeMode(ThemeMode.DARK)
        s.setHomeGrid(5, 6)
        s.setAppSort(AppSort.RECENTLY_INSTALLED)
        s.setShowDrawerLabels(false)
        s.setDrawerColumns(6)
        closeSettings()
        s = openSettings()
        val v = s.settings.first()
        assertEquals(ThemeMode.DARK, v.themeMode)
        assertEquals(5, v.homeColumns); assertEquals(6, v.homeRows)
        assertEquals(AppSort.RECENTLY_INSTALLED, v.appSort)
        assertFalse(v.showDrawerLabels)
        assertEquals(6, v.drawerColumns)
    }

    @Test fun homeLayoutOperationsAndRestore() = runBlocking {
        var db = openDb()
        var settings = openSettings()
        settings.setHomeGrid(4, 5)
        var home = HomeRepository(db, settings)

        val a = home.addAppToDesktop("a/.M#0", 0) as HomeRepository.Result.Placed
        val b = home.addAppToDesktop("b/.M#0", 0) as HomeRepository.Result.Placed
        assertEquals(HomeRepository.Result.AlreadyThere, home.addAppToDesktop("a/.M#0", 0))
        val widget = home.addToDesktop(HomeItemEntity(container = CONTAINER_DESKTOP, type = TYPE_WIDGET, widgetId = 77, spanX = 4, spanY = 2), 0) as HomeRepository.Result.Placed
        assertTrue(home.addToDock("c/.M#0", 4) is HomeRepository.Result.Placed)

        // Folder: drop b onto a.
        val folderId = home.createFolder(a.id, b.id)!!
        val folder = db.homeDao().get(folderId)!!
        assertEquals(TYPE_FOLDER, folder.type)
        assertEquals(2, db.homeDao().itemsIn(CONTAINER_FOLDER, folderId).size)
        home.renameFolder(folderId, "Redes")
        home.addAppToFolder(folderId, "d/.M#0")
        assertEquals(3, db.homeDao().itemsIn(CONTAINER_FOLDER, folderId).size)

        // Move + resize the widget, and verify collision checks.
        assertTrue(home.moveToDesktop(widget.id, 0, 0, 3))
        assertTrue(home.resize(widget.id, 0, 3, 3, 2))
        assertFalse(home.resize(widget.id, 0, 3, 3, 3)) // out of bounds
        db.close(); closeSettings()

        // "Restart": everything is restored with the same positions and sizes.
        db = openDb(); settings = openSettings(); home = HomeRepository(db, settings)
        val all = db.homeDao().getAll()
        val w = all.first { it.type == TYPE_WIDGET }
        assertEquals(listOf(0, 3, 3, 2), listOf(w.x, w.y, w.spanX, w.spanY))
        assertEquals(77, w.widgetId)
        assertEquals("Redes", all.first { it.id == folderId }.title)
        assertEquals(1, all.count { it.container == CONTAINER_DOCK })
        assertEquals(listOf(77), home.widgetIds())

        // Uninstalling apps removes their items; a folder left with one app becomes that app.
        home.removeApps(listOf("a/.M#0", "d/.M#0"), null, null)
        val after = db.homeDao().getAll()
        assertNull(after.firstOrNull { it.id == folderId })
        val remaining = after.first { it.appKey == "b/.M#0" }
        assertEquals(CONTAINER_DESKTOP, remaining.container)
        assertEquals(folder.x to folder.y, remaining.x to remaining.y)

        // Deleting the widget item returns its id so the host can release it.
        assertEquals(77, home.delete(w.id))
        db.close()
    }

    @Test fun gridChangeRepacksDesktopAndDockWithoutLoss() = runBlocking {
        val db = openDb()
        val settings = openSettings()
        settings.setHomeGrid(5, 5)
        val home = HomeRepository(db, settings)
        repeat(25) { home.addAppToDesktop("app$it/.M#0", 0) }
        repeat(5) { home.addToDock("dock$it/.M#0", 5) }
        assertEquals(1, settings.current().homePages)

        home.applyGrid(4, 4)
        val s = settings.current()
        assertEquals(4, s.homeColumns)
        val items = db.homeDao().getAll()
        assertEquals(30, items.size) // nothing lost
        val desktop = items.filter { it.container == CONTAINER_DESKTOP }
        assertTrue(desktop.all { it.x + it.spanX <= 4 && it.y + it.spanY <= 4 && it.containerId < s.homePages })
        assertEquals(desktop.size, desktop.map { Triple(it.containerId, it.x, it.y) }.toSet().size) // no overlaps
        assertTrue(items.filter { it.container == CONTAINER_DOCK }.all { it.x < 4 })
        assertTrue(s.homePages >= 2)

        // Empty pages can be removed, non-empty cannot.
        home.applyGrid(4, 4)
        assertFalse(home.removePage(0))
        db.close()
    }

    @Test fun duplicateInsertionsAreIgnored() = runBlocking {
        val db = openDb()
        val settings = openSettings()
        settings.setHomeGrid(4, 5)
        val home = HomeRepository(db, settings)
        assertTrue(home.addToDock("x/.M#0", 4) is HomeRepository.Result.Placed)
        assertEquals(HomeRepository.Result.AlreadyThere, home.addToDock("x/.M#0", 4))
        repeat(3) { home.addToDock("y$it/.M#0", 4) }
        assertEquals(HomeRepository.Result.NoSpace, home.addToDock("z/.M#0", 4))
        val folder = home.createFolderWithApp("f1/.M#0", (home.addAppToDesktop("f0/.M#0", 0) as HomeRepository.Result.Placed).id)!!
        assertFalse(home.addAppToFolder(folder, "f1/.M#0"))
        assertEquals(TYPE_APP, db.homeDao().itemsIn(CONTAINER_FOLDER, folder).first().type)
        db.close()
    }

    /** Builds a real v1 database from the exported schema, then lets Room migrate it to the current version. */
    @Test fun migrationFromV1KeepsDataAndAddsUsageAndPairs() = runBlocking {
        val schema = org.json.JSONObject(File("schemas/com.minimal.launcher.data.db.LauncherDatabase/1.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
            raw.execSQL("INSERT INTO categories (id, name, iconKey, position, builtinKey, keywords) VALUES (7, 'Conducción', 'driving', 0, 'driving', '')")
            raw.execSQL("INSERT INTO hidden_apps (appKey) VALUES ('secret/.Main#0')")
            raw.execSQL("INSERT INTO home_items (container, containerId, x, y, spanX, spanY, type, appKey, userSerial) VALUES (1, 0, 2, 0, 1, 1, 0, 'dock/.Main#0', 0)")
            raw.version = 1
        }

        val db = openDb()
        assertEquals("Conducción", db.categoryDao().getAll().single().name)
        assertEquals(listOf("secret/.Main#0"), db.hiddenAppDao().observe().first())
        assertEquals("dock/.Main#0", db.homeDao().getAll().single().appKey)

        val usage = db.usageDao()
        usage.record("uber/.Main#0", slot = 2, day = 3, now = 100)
        usage.record("uber/.Main#0", slot = 2, day = 3, now = 200)
        usage.record("uber/.Main#0", slot = 4, day = 3, now = 300)
        val rows = usage.getAll().sortedBy { it.timeSlot }
        assertEquals(listOf(2, 1), rows.map { it.count })
        assertEquals(200L, rows.first().lastOpened)

        val pairs = db.pairDao()
        val id = pairs.insert(com.minimal.launcher.data.db.AppPairEntity(apps = com.minimal.launcher.data.db.AppPairEntity.encode(listOf("uber/.Main#0", "didi/.Main#0")), categoryId = 7))
        assertEquals(listOf("uber/.Main#0", "didi/.Main#0"), pairs.get(id)!!.appKeys)
        db.close()

        // Survives a restart.
        val reopened = openDb()
        assertEquals(2, reopened.usageDao().getAll().size)
        assertEquals(1, reopened.pairDao().getAll().size)
        reopened.close()
    }
}
