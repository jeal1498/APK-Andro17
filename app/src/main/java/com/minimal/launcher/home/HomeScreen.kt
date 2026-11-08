package com.minimal.launcher.home

import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.minimal.launcher.LauncherViewModel
import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.data.db.HomeItemEntity
import com.minimal.launcher.data.prefs.LauncherSettings
import com.minimal.launcher.drawer.AppDrawer
import com.minimal.launcher.drawer.DrawerDragCallbacks
import com.minimal.launcher.ui.DeviceProfile
import com.minimal.launcher.ui.components.AppIconImage
import com.minimal.launcher.ui.components.AppTile
import com.minimal.launcher.ui.components.LauncherMenu
import com.minimal.launcher.ui.components.LocalAppActions
import com.minimal.launcher.ui.components.MenuAction
import com.minimal.launcher.ui.components.detectLauncherGestures
import com.minimal.launcher.ui.components.wallpaperLabelStyle
import com.minimal.launcher.ui.theme.isDarkTheme
import com.minimal.launcher.ui.theme.rememberWallpaperPrefersDarkText
import com.minimal.launcher.widgets.WidgetPickerSheet
import com.minimal.launcher.data.prefs.FrequentPosition
import com.minimal.launcher.ui.components.AppRow
import com.minimal.launcher.usage.FrequentPlacement
import com.minimal.launcher.pairs.AccessibilityDialog
import com.minimal.launcher.pairs.LauncherAccessibilityService
import com.minimal.launcher.pairs.PairPickerDialog
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Actions that need the Activity (activity results, other screens). */
interface HostActions {
    fun addWidget(info: AppWidgetProviderInfo, page: Int, cellWidthDp: Float, cellHeightDp: Float)
    fun reconfigureWidget(appWidgetId: Int)
    fun openSettings()
    fun openWallpaperPicker()
}

private sealed interface HomeMenu {
    data class Item(val id: Long) : HomeMenu
    data class Background(val page: Int, val offset: Offset) : HomeMenu
}

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    settings: LauncherSettings,
    profile: DeviceProfile,
    host: HostActions,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val actions = LocalAppActions.current

    val items = vm.homeItems.collectAsState().value.orEmpty()
    val allApps by vm.allApps.collectAsState()
    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
    val folderChildren = remember(items) {
        items.filter { it.container == HomeItemEntity.CONTAINER_FOLDER }.groupBy { it.containerId }.mapValues { e -> e.value.sortedBy { it.x } }
    }
    val dockItems = remember(items) { items.filter { it.container == HomeItemEntity.CONTAINER_DOCK } }

    val cols = settings.homeColumns.coerceAtLeast(1)
    val rows = settings.homeRows.coerceAtLeast(1)
    val drag = remember { DragController() }
    val pageCount = settings.homePages + if (drag.isDragging) 1 else 0
    val pagerState = rememberPagerState { pageCount }

    val darkText = rememberWallpaperPrefersDarkText()
    val wallpaperText = if (darkText) Color(0xFF1B1B1F) else Color.White
    val labelStyle = wallpaperLabelStyle(darkText)

    var menu by remember { mutableStateOf<HomeMenu?>(null) }
    var resizingId by remember { mutableStateOf<Long?>(null) }
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    val drawerProgress = remember { Animatable(0f) }
    var drawerSearchFocus by remember { mutableStateOf(false) }
    var drawerDragging by remember { mutableStateOf(false) }
    var lastCellDp by remember { mutableStateOf(80f to 100f) }
    var editMode by remember { mutableStateOf(false) }
    var pairFor by remember { mutableStateOf<AppInfo?>(null) }
    var showAccessibility by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.accessibilityRequest.collect { showAccessibility = true } }
    val frequent by vm.frequentApps.collectAsState()
    val frequentPosition = FrequentPlacement.effective(settings.frequentPosition, profile.isTablet)
    val thresholdPx = with(density) { 12.dp.toPx() }

    fun openDrawer(search: Boolean = false) {
        drawerSearchFocus = search
        scope.launch { drawerProgress.animateTo(1f, spring(stiffness = 500f, dampingRatio = 0.9f)) }
    }
    fun closeDrawer() {
        drawerSearchFocus = false
        scope.launch { drawerProgress.animateTo(0f, spring(stiffness = 500f, dampingRatio = 1f)) }
    }

    // Home button while already home: close overlays, then go back to the first page.
    LaunchedEffect(Unit) {
        vm.homePressed.collect {
            val overlay = drawerProgress.value > 0f || openFolderId != null || resizingId != null || showWidgetPicker || menu != null || editMode
            menu = null; resizingId = null; openFolderId = null; showWidgetPicker = false; editMode = false
            if (drawerProgress.value > 0f) closeDrawer()
            if (!overlay) pagerState.animateScrollToPage(0)
        }
    }
    BackHandler(enabled = resizingId != null || openFolderId != null || menu != null || editMode) {
        when {
            menu != null -> menu = null
            editMode && resizingId == null && openFolderId == null -> editMode = false
            resizingId != null -> resizingId = null
            else -> openFolderId = null
        }
    }

    // Status bar icons follow the wallpaper on home and the theme in the drawer.
    val view = LocalView.current
    val darkTheme = isDarkTheme(settings.themeMode)
    val drawerMostlyOpen = drawerProgress.value > 0.5f
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val lightBars = if (drawerMostlyOpen) !darkTheme else darkText
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
    }

    // Auto-flip pages while dragging near the screen edges.
    LaunchedEffect(drag.isDragging) {
        if (!drag.isDragging) return@LaunchedEffect
        val edge = with(density) { 28.dp.toPx() }
        var edgeTime = 0L
        while (drag.isDragging) {
            delay(100)
            val x = drag.pointer.x
            val width = view.width.toFloat()
            val dir = when {
                x < edge -> -1
                x > width - edge -> 1
                else -> 0
            }
            edgeTime = if (dir != 0 && drag.moved) edgeTime + 100 else 0
            if (edgeTime >= 600) {
                val target = (pagerState.currentPage + dir).coerceIn(0, pagerState.pageCount - 1)
                if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
                edgeTime = 0
            }
        }
    }
    // Dismiss the long-press menu once the item actually starts moving.
    LaunchedEffect(drag) {
        snapshotFlow { drag.moved }.collect { if (it) menu = null }
    }

    fun onDrop(cancelled: Boolean) {
        val payload = drag.payload ?: return
        val moved = drag.moved
        val pointer = drag.pointer
        val topLeft = drag.topLeft
        drag.clear()
        drawerDragging = false
        if (cancelled || !moved) return
        // Dock
        val dock = drag.dockBounds
        if (dock.contains(pointer)) {
            if (!payload.dockable) return
            val slot = if (profile.dockOnSide) floor((pointer.y - dock.top) / (dock.height / cols)).toInt()
            else floor((pointer.x - dock.left) / (dock.width / cols)).toInt()
            val s = slot.coerceIn(0, cols - 1)
            val occupant = dockItems.firstOrNull { it.x == s && it.id != payload.itemId }
            when {
                occupant != null && payload.folderable && occupant.type in HomeRepository.FOLDERABLE ->
                    if (payload.itemId != null) vm.createFolder(occupant.id, payload.itemId) else vm.createFolderWithApp(occupant.id, payload.appKey!!)
                occupant != null && payload.folderable && occupant.type == HomeItemEntity.TYPE_FOLDER ->
                    if (payload.itemId != null) vm.addToFolder(occupant.id, payload.itemId) else vm.addAppToFolder(occupant.id, payload.appKey!!)
                payload.itemId != null -> vm.moveToDock(payload.itemId, s)
                else -> vm.addAppToDockSlot(payload.appKey!!, s)
            }
            return
        }
        // Grid
        val origin = drag.gridOrigin
        val size = drag.gridSize
        if (size.width == 0) return
        val cellW = size.width.toFloat() / cols
        val cellH = size.height.toFloat() / rows
        val page = pagerState.currentPage
        val local = topLeft - origin
        val (tx, ty) = GridPlacement.cellForPosition(local.x, local.y, cellW, cellH, payload.spanX, payload.spanY, cols, rows)
        val pointerLocal = pointer - origin
        val pcx = floor(pointerLocal.x / cellW).toInt()
        val pcy = floor(pointerLocal.y / cellH).toInt()
        val occupant = GridPlacement.itemAt(items, page, pcx, pcy, excludeId = payload.itemId)
        if (occupant != null && payload.folderable) {
            if (occupant.type in HomeRepository.FOLDERABLE) {
                if (payload.itemId != null) vm.createFolder(occupant.id, payload.itemId) else vm.createFolderWithApp(occupant.id, payload.appKey!!)
                return
            }
            if (occupant.type == HomeItemEntity.TYPE_FOLDER) {
                if (payload.itemId != null) vm.addToFolder(occupant.id, payload.itemId) else vm.addAppToFolder(occupant.id, payload.appKey!!)
                return
            }
        }
        if (payload.itemId != null) vm.moveToDesktop(payload.itemId, page, tx, ty)
        else vm.addAppAt(payload.appKey!!, page, tx, ty)
    }

    fun openItem(item: HomeItemEntity, local: Offset = Offset.Zero, size: IntSize = IntSize.Zero) {
        when (item.type) {
            HomeItemEntity.TYPE_APP -> appsByKey[item.appKey]?.let { vm.launch(it) }
            HomeItemEntity.TYPE_SHORTCUT -> {
                val user = vm.shortcutUser(item.userSerial) ?: return
                actions.startShortcut(item.packageName ?: return, item.shortcutId ?: return, user)
            }
            HomeItemEntity.TYPE_FOLDER -> openFolderId = item.id
            HomeItemEntity.TYPE_CLOCK -> actions.startIntent(if (size.height > 0 && local.y > size.height * 0.62f) calendarIntent() else clockIntent())
        }
    }

    val cellCallbacks = object : CellCallbacks {
        override fun onItemTap(item: HomeItemEntity, localInItem: Offset, itemSize: IntSize) {
            menu = null
            openItem(item, localInItem, itemSize)
        }
        override fun onItemLongPress(item: HomeItemEntity, pointerRoot: Offset, grab: Offset, sizePx: IntSize) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            resizingId = null
            menu = HomeMenu.Item(item.id)
            drag.start(DragPayload(item.id, item.appKey, item.type, item.spanX, item.spanY, sizePx), pointerRoot, grab)
        }
        override fun onBackgroundLongPress(page: Int, local: Offset) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            menu = HomeMenu.Background(page, local)
        }
        override fun onBackgroundTap() {
            menu = null
            resizingId = null
        }
        override fun onDrag(delta: Offset) = drag.moveBy(delta, thresholdPx)
        override fun onDragEnd(cancelled: Boolean) = onDrop(cancelled)
        override fun onResize(item: HomeItemEntity, x: Int, y: Int, spanX: Int, spanY: Int) {
            if (x != item.x || y != item.y || spanX != item.spanX || spanY != item.spanY) vm.resizeItem(item.id, x, y, spanX, spanY)
        }
    }

    fun itemActions(item: HomeItemEntity): List<MenuAction> = buildList {
        val app = item.appKey?.let { appsByKey[it] }
        when (item.type) {
            HomeItemEntity.TYPE_APP -> if (app != null) {
                add(MenuAction("Combinar en par", Icons.Outlined.VerticalSplit) { pairFor = app })
                add(MenuAction("Información de la app", Icons.Outlined.Info) { actions.openAppInfo(app) })
            }
            HomeItemEntity.TYPE_FOLDER -> {
                add(MenuAction("Abrir", Icons.Outlined.FolderOpen) { openFolderId = item.id })
                add(MenuAction("Desagrupar", Icons.Outlined.Edit) { vm.ungroupFolder(item.id, pagerState.currentPage) })
            }
            HomeItemEntity.TYPE_WIDGET -> {
                val info = item.widgetId?.let { vm.container.widgets.info(it) }
                if (vm.container.widgets.canResize(info)) add(MenuAction("Redimensionar", Icons.Outlined.AspectRatio) { resizingId = item.id })
                if (vm.container.widgets.isReconfigurable(info)) add(MenuAction("Configurar", Icons.Outlined.Settings) { host.reconfigureWidget(item.widgetId!!) })
            }
            HomeItemEntity.TYPE_CLOCK -> add(MenuAction("Redimensionar", Icons.Outlined.AspectRatio) { resizingId = item.id })
        }
        add(MenuAction("Quitar", Icons.Outlined.Close) { vm.removeItem(item.id) })
        if (item.type == HomeItemEntity.TYPE_APP && app != null) {
            add(MenuAction("Desinstalar", Icons.Outlined.DeleteOutline) { actions.uninstall(app) })
        }
    }

    @Composable
    fun ItemMenuOverlay(item: HomeItemEntity) {
        val m = menu
        if (m is HomeMenu.Item && m.id == item.id) {
            val app = if (item.type == HomeItemEntity.TYPE_APP) item.appKey?.let { appsByKey[it] } else null
            LauncherMenu(
                expanded = true,
                onDismiss = { menu = null },
                actions = itemActions(item),
                app = app,
                onPinShortcut = app?.let { a -> { id, label -> vm.addShortcut(a.packageName, id, label, a.userSerial, pagerState.currentPage) } },
            )
        }
    }

    fun resizeLimits(item: HomeItemEntity, m: CellMetrics): ResizeLimits {
        val wDp = with(density) { m.cellW.toDp().value }
        val hDp = with(density) { m.cellH.toDp().value }
        if (item.type == HomeItemEntity.TYPE_CLOCK) return ResizeLimits(2, 1, cols, rows.coerceAtMost(3), true, true)
        val widgets = vm.container.widgets
        val info = item.widgetId?.let { widgets.info(it) }
        val (minX, minY) = widgets.minSpan(info, wDp, hDp, cols, rows)
        val (maxX, maxY) = widgets.maxSpan(info, wDp, hDp, cols, rows)
        val mode = info?.resizeMode ?: 0
        return ResizeLimits(
            minX.coerceAtMost(item.spanX), minY.coerceAtMost(item.spanY),
            maxX.coerceAtLeast(item.spanX), maxY.coerceAtLeast(item.spanY),
            mode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            mode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
        )
    }

    val drawerCallbacks = object : DrawerDragCallbacks {
        override fun onStart(app: AppInfo, pointerRoot: Offset) {
            val cellW = drag.gridSize.width.toFloat() / cols
            val cellH = drag.gridSize.height.toFloat() / rows
            val size = IntSize(cellW.roundToInt(), cellH.roundToInt())
            drawerDragging = true
            drag.start(
                DragPayload(null, app.key, HomeItemEntity.TYPE_APP, 1, 1, size),
                pointerRoot, Offset(size.width / 2f, size.height / 2f),
            )
            drag.moveBy(Offset(0f, 0.1f), 0f)
            // The drawer fades out in place (no translation) so the ongoing gesture keeps stable coordinates.
            scope.launch { drawerProgress.snapTo(0f) }
        }
        override fun onDrag(delta: Offset) = drag.moveBy(delta, thresholdPx)
        override fun onEnd(cancelled: Boolean) = onDrop(cancelled)
    }

    fun frequentActions(app: AppInfo): List<MenuAction> = buildList {
        val pinned = app.key in settings.pinnedFrequent
        add(MenuAction(if (pinned) "Quitar de Frecuentes" else "Fijar en Frecuentes", Icons.Outlined.PushPin) { vm.setFrequentPinned(app.key, !pinned) })
        add(MenuAction("Mover la fila", Icons.Outlined.OpenWith) { editMode = true })
        add(MenuAction("Información de la app", Icons.Outlined.Info) { actions.openAppInfo(app) })
    }

    /** The "Frecuentes" row. In edit mode it can be dragged to the top, bottom or (tablets) the side. */
    @Composable
    fun FrequentStrip(vertical: Boolean, modifier: Modifier = Modifier) {
        var offset by remember { mutableStateOf(Offset.Zero) }
        var bounds by remember { mutableStateOf(Rect.Zero) }
        val editing = editMode
        Box(
            modifier
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .then(
                    if (editing) Modifier
                        .border(2.dp, wallpaperText.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                        .pointerInput(profile.isTablet) {
                            detectDragGestures(
                                onDragEnd = {
                                    val c = bounds.center + offset
                                    offset = Offset.Zero
                                    vm.setFrequentPosition(FrequentPlacement.dropTarget(c.x, c.y, view.width.toFloat(), view.height.toFloat(), profile.isTablet))
                                },
                                onDragCancel = { offset = Offset.Zero },
                            ) { change, delta -> change.consume(); offset += delta }
                        }
                    else Modifier,
                )
                .padding(vertical = 4.dp),
        ) {
            AppRow(
                title = "Frecuentes",
                apps = frequent,
                iconSize = profile.maxIconSize * 0.82f,
                labelStyle = labelStyle,
                showLabels = settings.showHomeLabels && !vertical,
                onLaunch = { vm.launch(it) },
                menuFor = ::frequentActions,
                vertical = vertical,
                testTag = "frequent_home",
                onTitleLongPress = { editMode = true },
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        // ---------------- Home content ----------------
        val velocity = remember { VelocityTracker() }
        val progressValue = drawerProgress.value
        Box(
            Modifier
                .fillMaxSize()
                .testTag("home")
                .graphicsLayer {
                    // Alpha only: scaling would shift the drop-target geometry used while dragging.
                    alpha = 1f - progressValue
                }
                .pointerInput(Unit) {
                    val heightPx = size.height.toFloat()
                    detectVerticalDragGestures(
                        onDragStart = { velocity.resetTracking() },
                        onDragEnd = {
                            val v = velocity.calculateVelocity().y
                            if (drawerProgress.value > 0.25f || v < -800f) openDrawer() else closeDrawer()
                        },
                        onDragCancel = { closeDrawer() },
                    ) { change, dy ->
                        if (drag.isDragging) return@detectVerticalDragGestures
                        change.consume()
                        velocity.addPosition(change.uptimeMillis, change.position)
                        menu = null
                        scope.launch { drawerProgress.snapTo((drawerProgress.value - dy / (heightPx * 0.6f)).coerceIn(0f, 1f)) }
                    }
                }
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val pagesArea: @Composable (Modifier) -> Unit = { mod ->
                Column(mod) {
                    if (editMode) EditModeBar(
                        frequentHidden = settings.frequentPosition == FrequentPosition.HIDDEN,
                        onToggleFrequent = {
                            vm.setFrequentPosition(if (settings.frequentPosition == FrequentPosition.HIDDEN) FrequentPosition.TOP else FrequentPosition.HIDDEN)
                        },
                        onDone = { editMode = false },
                    )
                    if (frequentPosition == FrequentPosition.TOP) FrequentStrip(false, Modifier.fillMaxWidth().padding(horizontal = profile.homePadding))
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (frequentPosition == FrequentPosition.SIDEBAR) FrequentStrip(true, Modifier.fillMaxHeight().padding(start = 8.dp))
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        beyondViewportPageCount = 1,
                        key = { it },
                    ) { page ->
                        Box(Modifier.fillMaxSize().padding(horizontal = profile.homePadding)) {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val cw = maxWidth.value / cols
                                val ch = maxHeight.value / rows
                                if (page == pagerState.currentPage) SideEffect { lastCellDp = cw to ch }
                            }
                            CellLayout(
                                page = page,
                                isCurrentPage = page == pagerState.currentPage,
                                items = items,
                                folderChildren = folderChildren,
                                apps = appsByKey,
                                cols = cols,
                                rows = rows,
                                maxIconSize = profile.maxIconSize,
                                showLabels = settings.showHomeLabels,
                                labelStyle = labelStyle,
                                wallpaperTextColor = wallpaperText,
                                darkText = darkText,
                                drag = drag,
                                hiddenItemId = if (drag.moved) drag.payload?.itemId else null,
                                resizingItemId = resizingId,
                                resizeLimits = ::resizeLimits,
                                callbacks = cellCallbacks,
                                overlay = { item -> ItemMenuOverlay(item) },
                            )
                            val bg = menu
                            if (bg is HomeMenu.Background && bg.page == page) {
                                Box(Modifier.offset { IntOffset(bg.offset.x.roundToInt(), bg.offset.y.roundToInt()) }) {
                                    val pageEmpty = items.none { it.container == HomeItemEntity.CONTAINER_DESKTOP && it.containerId.toInt() == page }
                                    LauncherMenu(
                                        expanded = true,
                                        onDismiss = { menu = null },
                                        actions = buildList {
                                            add(MenuAction("Widgets", Icons.Outlined.Widgets) { showWidgetPicker = true })
                                            add(MenuAction("Fondo de pantalla", Icons.Outlined.Wallpaper) { host.openWallpaperPicker() })
                                            add(MenuAction("Editar inicio", Icons.Outlined.Edit) { editMode = true })
                                            add(MenuAction("Ajustes de inicio", Icons.Outlined.Settings) { host.openSettings() })
                                            add(MenuAction("Añadir página", Icons.Outlined.AddBox) {
                                                vm.addPage()
                                                scope.launch { delay(150); pagerState.animateScrollToPage(settings.homePages) }
                                            })
                                            if (pageEmpty && settings.homePages > 1) add(MenuAction("Eliminar página", Icons.Outlined.Delete) { vm.removePage(page) })
                                        },
                                    )
                                }
                            }
                        }
                    }
                    }
                    PageIndicator(pagerState.pageCount, pagerState.currentPage, wallpaperText)
                    if (frequentPosition == FrequentPosition.BOTTOM) FrequentStrip(false, Modifier.fillMaxWidth().padding(horizontal = profile.homePadding))
                }
            }

            if (profile.dockOnSide) {
                Row(Modifier.fillMaxSize()) {
                    pagesArea(Modifier.weight(1f).fillMaxHeight())
                    Dock(
                        items = dockItems, slots = cols, vertical = true, appsByKey = appsByKey, folderChildren = folderChildren,
                        iconSize = profile.maxIconSize.coerceAtMost(52.dp), drag = drag, labelStyle = labelStyle,
                        onTap = { openItem(it) },
                        onLongPress = { item, pointerRoot, grab, sizePx -> cellCallbacks.onItemLongPress(item, pointerRoot, grab, sizePx) },
                        onDrag = { drag.moveBy(it, thresholdPx) },
                        onDragEnd = ::onDrop,
                        menuOverlay = { ItemMenuOverlay(it) },
                        modifier = Modifier.fillMaxHeight().width(88.dp),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    pagesArea(Modifier.weight(1f).fillMaxWidth())
                    Dock(
                        items = dockItems, slots = cols, vertical = false, appsByKey = appsByKey, folderChildren = folderChildren,
                        iconSize = profile.maxIconSize, drag = drag, labelStyle = labelStyle,
                        onTap = { openItem(it) },
                        onLongPress = { item, pointerRoot, grab, sizePx -> cellCallbacks.onItemLongPress(item, pointerRoot, grab, sizePx) },
                        onDrag = { drag.moveBy(it, thresholdPx) },
                        onDragEnd = ::onDrop,
                        menuOverlay = { ItemMenuOverlay(it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = profile.homePadding).height(profile.maxIconSize + 24.dp),
                    )
                    if (settings.showSearchBar) {
                        SearchPill(
                            onClick = { openDrawer(search = true) },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = profile.homePadding + 8.dp)
                                .padding(top = 4.dp, bottom = 12.dp)
                                .widthIn(max = 640.dp)
                                .fillMaxWidth(),
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // ---------------- App drawer ----------------
        if (drawerProgress.value > 0f || drawerDragging) {
            AppDrawer(
                vm = vm,
                settings = settings,
                profile = profile,
                progress = drawerProgress,
                focusSearch = drawerSearchFocus,
                currentPage = pagerState.currentPage,
                onClose = ::closeDrawer,
                onOpen = { openDrawer() },
                dragCallbacks = drawerCallbacks,
                hiddenForDrag = drawerDragging,
            )
        }

        // ---------------- Drag shadow ----------------
        val payload = drag.payload
        if (payload != null && drag.moved) {
            val tl = drag.topLeft
            Box(
                Modifier
                    .offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
                    .size(with(density) { payload.sizePx.width.toDp() }, with(density) { payload.sizePx.height.toDp() })
                    .graphicsLayer { scaleX = 1.06f; scaleY = 1.06f; alpha = 0.92f },
                contentAlignment = Alignment.Center,
            ) {
                when (payload.type) {
                    HomeItemEntity.TYPE_WIDGET, HomeItemEntity.TYPE_CLOCK -> Box(
                        Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)),
                    )
                    else -> {
                        val item = payload.itemId?.let { id -> items.firstOrNull { it.id == id } }
                        val app = payload.appKey?.let { appsByKey[it] }
                        if (item != null) {
                            ItemIcon(item, app, folderChildren[item.id].orEmpty(), profile.maxIconSize)
                        } else if (app != null) {
                            AppIconImage(app.key, profile.maxIconSize)
                        }
                    }
                }
            }
        }

        // ---------------- Folders, widget picker ----------------
        val folder = openFolderId?.let { id -> items.firstOrNull { it.id == id } }
        if (folder != null) {
            FolderDialog(
                folder = folder,
                children = folderChildren[folder.id].orEmpty(),
                apps = appsByKey,
                allApps = vm.visibleApps.collectAsState().value,
                profile = profile,
                onDismiss = { openFolderId = null },
                onOpen = { openItem(it) },
                onRename = { vm.renameFolder(folder.id, it) },
                onRemoveFromFolder = { vm.removeFromFolder(it.id, pagerState.currentPage) },
                onRemoveItem = { vm.removeItem(it.id) },
                onAddApps = { keys -> keys.forEach { vm.addAppToFolder(folder.id, it) } },
                onUngroup = { openFolderId = null; vm.ungroupFolder(folder.id, pagerState.currentPage) },
                onAppInfo = { app -> actions.openAppInfo(app) },
            )
        }
        pairFor?.let { first ->
            PairPickerDialog(first, vm.visibleApps.collectAsState().value, onDismiss = { pairFor = null }) { keys -> pairFor = null; vm.createPair(keys) }
        }
        if (showAccessibility) {
            AccessibilityDialog(
                onOpenSettings = { actions.startIntent(LauncherAccessibilityService.settingsIntent()) },
                onDismiss = { showAccessibility = false; vm.markAccessibilityPrompted() },
            )
        }
        if (showWidgetPicker) {
            WidgetPickerSheet(
                profile = profile,
                onDismiss = { showWidgetPicker = false },
                onPickClock = { showWidgetPicker = false; vm.addClock(pagerState.currentPage) },
                onPick = { info ->
                    showWidgetPicker = false
                    host.addWidget(info, pagerState.currentPage, lastCellDp.first, lastCellDp.second)
                },
            )
        }
    }
}

@Composable
private fun EditModeBar(frequentHidden: Boolean, onToggleFrequent: () -> Unit, onDone: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("edit_mode_bar"),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (frequentHidden) "Modo edición" else "Arrastra «Frecuentes» arriba, abajo o a un lado",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToggleFrequent) { Text(if (frequentHidden) "Mostrar Frecuentes" else "Ocultar") }
            FilledTonalButton(onClick = onDone, modifier = Modifier.testTag("edit_done")) { Text("Listo") }
        }
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int, color: Color) {
    Row(
        Modifier.fillMaxWidth().height(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count <= 1) return@Row
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (i == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (i == current) 0.95f else 0.45f)),
            )
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .testTag("search_pill")
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text("Buscar apps", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Dock(
    items: List<HomeItemEntity>,
    slots: Int,
    vertical: Boolean,
    appsByKey: Map<String, AppInfo>,
    folderChildren: Map<Long, List<HomeItemEntity>>,
    iconSize: androidx.compose.ui.unit.Dp,
    drag: DragController,
    labelStyle: androidx.compose.ui.text.TextStyle,
    onTap: (HomeItemEntity) -> Unit,
    onLongPress: (HomeItemEntity, Offset, Offset, IntSize) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Boolean) -> Unit,
    menuOverlay: @Composable (HomeItemEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val current by rememberUpdatedState(items)
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    Box(
        modifier
            .testTag("dock")
            .onGloballyPositioned { bounds = it.boundsInRoot(); drag.dockBounds = bounds }
            .pointerInput(slots, vertical) {
                fun slotAt(p: Offset): Int {
                    val s = if (vertical) p.y / (size.height.toFloat() / slots) else p.x / (size.width.toFloat() / slots)
                    return floor(s).toInt().coerceIn(0, slots - 1)
                }
                detectLauncherGestures(
                    onTap = { p -> current.firstOrNull { it.x == slotAt(p) }?.let(tap) },
                    onLongPress = { p ->
                        val slot = slotAt(p)
                        val item = current.firstOrNull { it.x == slot } ?: return@detectLauncherGestures
                        val cell = if (vertical) IntSize(size.width, size.height / slots) else IntSize(size.width / slots, size.height)
                        val topLeft = if (vertical) Offset(0f, slot * cell.height.toFloat()) else Offset(slot * cell.width.toFloat(), 0f)
                        longPress(item, Offset(bounds.left, bounds.top) + p, p - topLeft, cell)
                    },
                    onDrag = { _, d -> onDrag(d) },
                    onDragEnd = onDragEnd,
                )
            },
    ) {
        val content: @Composable (Int) -> Unit = { slot ->
            val item = items.firstOrNull { it.x == slot }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (item != null) {
                    key(item.id) {
                        val app = item.appKey?.let { appsByKey[it] }
                        val hidden = drag.moved && drag.payload?.itemId == item.id
                        Box(Modifier.graphicsLayer { alpha = if (hidden) 0f else 1f }) {
                            AppTile(
                                icon = { size -> ItemIcon(item, app, folderChildren[item.id].orEmpty(), size) },
                                label = itemLabel(item, app),
                                iconSize = iconSize,
                                showLabel = false,
                                labelStyle = labelStyle,
                            )
                            menuOverlay(item)
                        }
                    }
                }
            }
        }
        if (vertical) {
            Column(Modifier.fillMaxSize()) { repeat(slots) { Box(Modifier.weight(1f).fillMaxWidth()) { content(it) } } }
        } else {
            Row(Modifier.fillMaxSize()) { repeat(slots) { Box(Modifier.weight(1f).fillMaxHeight()) { content(it) } } }
        }
    }
}
