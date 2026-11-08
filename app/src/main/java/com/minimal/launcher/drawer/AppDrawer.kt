package com.minimal.launcher.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.minimal.launcher.LauncherViewModel
import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.categories.CategoryWithApps
import com.minimal.launcher.categories.Resolution
import com.minimal.launcher.data.prefs.AppSort
import com.minimal.launcher.data.prefs.DrawerTab
import com.minimal.launcher.data.prefs.LauncherSettings
import com.minimal.launcher.search.AppSearch
import com.minimal.launcher.ui.DeviceProfile
import com.minimal.launcher.ui.components.AppIconImage
import com.minimal.launcher.ui.components.AppTile
import com.minimal.launcher.ui.components.CategoryIcons
import com.minimal.launcher.ui.components.LauncherMenu
import com.minimal.launcher.ui.components.LocalAppActions
import com.minimal.launcher.ui.components.MenuAction
import com.minimal.launcher.ui.components.detectLauncherGestures
import kotlinx.coroutines.launch

/** Lets the drawer hand an app over to the home screen when the user drags it out. */
interface DrawerDragCallbacks {
    fun onStart(app: AppInfo, pointerRoot: Offset)
    fun onDrag(delta: Offset)
    fun onEnd(cancelled: Boolean)
}

@Composable
fun AppDrawer(
    vm: LauncherViewModel,
    settings: LauncherSettings,
    profile: DeviceProfile,
    progress: Animatable<Float, AnimationVector1D>,
    focusSearch: Boolean,
    currentPage: Int,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    dragCallbacks: DrawerDragCallbacks,
    hiddenForDrag: Boolean,
) {
    val scope = rememberCoroutineScope()
    val apps by vm.visibleApps.collectAsState()
    val categories by vm.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    var openCategoryId by remember { mutableStateOf<Long?>(null) }
    var assignFor by remember { mutableStateOf<Pair<AppInfo, Resolution>?>(null) }
    var editing by remember { mutableStateOf<CategoryWithApps?>(null) }
    var creating by remember { mutableStateOf(false) }
    var addingTo by remember { mutableStateOf<CategoryWithApps?>(null) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val actions = LocalAppActions.current

    val closing = progress.targetValue == 0f
    LaunchedEffect(closing) {
        if (closing) {
            focusManager.clearFocus(); keyboard?.hide()
            query = ""; openCategoryId = null
        }
    }
    LaunchedEffect(focusSearch) {
        if (focusSearch) runCatching { focusRequester.requestFocus() }
    }
    BackHandler(enabled = !closing) {
        when {
            query.isNotEmpty() -> query = ""
            openCategoryId != null -> openCategoryId = null
            else -> onClose()
        }
    }

    // Pull down at the top of the list to close; fling up to reopen.
    val closeConnection = remember {
        object : NestedScrollConnection {
            var height = 1f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (progress.value < 1f && available.y < 0f && source == NestedScrollSource.UserInput) {
                    scope.launch { progress.snapTo((progress.value - available.y / height).coerceIn(0f, 1f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    scope.launch { progress.snapTo((progress.value - available.y / height).coerceIn(0f, 1f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (progress.value < 1f) {
                    if (progress.value < 0.75f || available.y > 1200f) onClose() else onOpen()
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .testTag("drawer")
            .onGloballyPositioned { closeConnection.height = it.size.height.toFloat() * 0.6f }
            .graphicsLayer {
                if (hiddenForDrag) {
                    alpha = 0f
                } else {
                    translationY = (1f - progress.value) * size.height * 0.35f
                    alpha = progress.value.coerceIn(0f, 1f)
                }
            }
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (progress.value < 0.75f) onClose() else onOpen() },
                    onDragCancel = { onOpen() },
                ) { change, dy ->
                    change.consume()
                    scope.launch { progress.snapTo((progress.value - dy / (size.height * 0.6f)).coerceIn(0f, 1f)) }
                }
            }
            .nestedScroll(closeConnection)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.fillMaxSize().widthIn(max = profile.drawerMaxWidth)) {
            SearchRow(
                query = query,
                onQuery = { query = it },
                focusRequester = focusRequester,
                onSubmit = { AppSearch.search(apps, query).firstOrNull()?.let { vm.launch(it) } },
                sort = settings.appSort,
                onSort = vm::setAppSort,
                onSettings = { actions.startIntent(android.content.Intent(vm.getApplication(), com.minimal.launcher.settings.SettingsActivity::class.java)) },
                onCreateCategory = { creating = true },
            )
            val menuFor: (AppInfo, CategoryWithApps?) -> List<MenuAction> = { app, inCategory ->
                buildList {
                    add(MenuAction("Añadir a inicio", Icons.Outlined.AddToHomeScreen) { vm.addToHome(app.key, currentPage) })
                    add(MenuAction("Añadir al dock", Icons.Outlined.Dock) { vm.addToDock(app.key) })
                    add(MenuAction("Categorías…", Icons.Outlined.Category) {
                        scope.launch { assignFor = app to vm.resolutionFor(app) }
                    })
                    if (inCategory != null && !inCategory.isUnclassified) {
                        add(MenuAction("Quitar de ${inCategory.name}", Icons.Outlined.RemoveCircleOutline) { vm.removeAppFromCategory(app, inCategory.id) })
                    }
                    add(MenuAction("Información de la app", Icons.Outlined.Info) { actions.openAppInfo(app) })
                    add(MenuAction("Ocultar", Icons.Outlined.VisibilityOff) { vm.hideApp(app.key) })
                    add(MenuAction("Desinstalar", Icons.Outlined.DeleteOutline) { actions.uninstall(app) })
                }
            }
            val gridPadding = PaddingValues(
                start = 12.dp, end = 12.dp, top = 8.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            )

            if (query.isNotBlank()) {
                val results = remember(apps, query) { AppSearch.search(apps, query) }
                if (results.isEmpty()) {
                    EmptyState("No se encontraron apps para “$query”")
                } else {
                    AppGrid(
                        apps = results, columns = profile.drawerColumns, iconSize = profile.drawerIconSize,
                        showLabels = settings.showDrawerLabels, contentPadding = gridPadding,
                        onLaunch = vm::launch, menuFor = { menuFor(it, null) }, dragCallbacks = dragCallbacks,
                        testTag = "search_results",
                    )
                }
            } else {
                SingleChoiceSegmentedButtonRow(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp).widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                    SegmentedButton(
                        selected = settings.drawerTab == DrawerTab.CATEGORIES,
                        onClick = { vm.setDrawerTab(DrawerTab.CATEGORIES); openCategoryId = null },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("tab_categories"),
                    ) { Text("Categorías") }
                    SegmentedButton(
                        selected = settings.drawerTab == DrawerTab.ALL,
                        onClick = { vm.setDrawerTab(DrawerTab.ALL) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("tab_all"),
                    ) { Text("Todas") }
                }
                if (settings.drawerTab == DrawerTab.ALL) {
                    if (apps.isEmpty()) EmptyState(if (vm.appsLoaded.collectAsState().value) "No hay apps visibles" else "Cargando apps…")
                    else AppGrid(
                        apps = apps, columns = profile.drawerColumns, iconSize = profile.drawerIconSize,
                        showLabels = settings.showDrawerLabels, contentPadding = gridPadding,
                        onLaunch = vm::launch, menuFor = { menuFor(it, null) }, dragCallbacks = dragCallbacks,
                        testTag = "all_apps",
                    )
                } else {
                    val open = categories.firstOrNull { it.id == openCategoryId }
                    AnimatedContent(
                        targetState = open,
                        transitionSpec = {
                            if (targetState != null) {
                                (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.88f)) togetherWith fadeOut(tween(120))
                            } else {
                                fadeIn(tween(200)) togetherWith (fadeOut(tween(150)) + scaleOut(tween(200), targetScale = 0.9f))
                            }
                        },
                        contentKey = { it?.id },
                        label = "category",
                    ) { category ->
                        if (category == null) {
                            CategoryGrid(
                                categories = categories,
                                columns = profile.categoryColumns,
                                contentPadding = gridPadding,
                                onOpen = { openCategoryId = it.id },
                                onEdit = { editing = it },
                                onCreate = { creating = true },
                            )
                        } else {
                            CategoryDetail(
                                category = category,
                                profile = profile,
                                settings = settings,
                                contentPadding = gridPadding,
                                onBack = { openCategoryId = null },
                                onEdit = { editing = category },
                                onAddApps = { addingTo = category },
                                onLaunch = vm::launch,
                                menuFor = { menuFor(it, category) },
                                dragCallbacks = dragCallbacks,
                            )
                        }
                    }
                }
            }
        }
    }

    assignFor?.let { (app, res) ->
        AssignCategoriesDialog(
            app = app, categories = categories, resolution = res,
            onDismiss = { assignFor = null },
            onSave = { vm.setAppCategories(app.key, it); assignFor = null },
            onRestoreAutomatic = { vm.restoreAutomatic(app.key); assignFor = null },
        )
    }
    editing?.let { c ->
        if (c.isUnclassified) { editing = null; return@let }
        CategoryEditorDialog(
            title = "Editar categoría",
            initialName = c.name, initialIcon = c.iconKey, initialKeywords = c.keywords,
            onDismiss = { editing = null },
            onSave = { n, i, k -> vm.updateCategory(c.id, n, i, k); editing = null },
            onDelete = { vm.deleteCategory(c.id); if (openCategoryId == c.id) openCategoryId = null; editing = null },
        )
    }
    if (creating) {
        CategoryEditorDialog(
            title = "Nueva categoría", initialName = "", initialIcon = "folder", initialKeywords = "",
            onDismiss = { creating = false },
            onSave = { n, i, k -> vm.createCategory(n, i, k); creating = false },
        )
    }
    addingTo?.let { c ->
        val existing = c.apps.map { it.key }.toSet()
        AppPickerDialog(
            title = "Añadir a ${c.name}",
            apps = apps.filter { it.key !in existing },
            onDismiss = { addingTo = null },
            onConfirm = { vm.addAppsToCategory(c.id, it); addingTo = null },
        )
    }
}

@Composable
private fun SearchRow(
    query: String,
    onQuery: (String) -> Unit,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    sort: AppSort,
    onSort: (AppSort) -> Unit,
    onSettings: () -> Unit,
    onCreateCategory: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Buscar apps") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Borrar") } }
            } else null,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("drawer_search"),
        )
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("drawer_more")) { Icon(Icons.Outlined.MoreVert, "Más opciones") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                listOf(
                    AppSort.ALPHABETICAL to "Orden A-Z",
                    AppSort.ALPHABETICAL_DESC to "Orden Z-A",
                    AppSort.RECENTLY_INSTALLED to "Instaladas recientemente",
                ).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, null) },
                        trailingIcon = if (value == sort) { { Text("✓") } } else null,
                        onClick = { menu = false; onSort(value) },
                    )
                }
                DropdownMenuItem(text = { Text("Nueva categoría") }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, onClick = { menu = false; onCreateCategory() })
                DropdownMenuItem(text = { Text("Ajustes") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { menu = false; onSettings() })
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AppGrid(
    apps: List<AppInfo>,
    columns: Int,
    iconSize: Dp,
    showLabels: Boolean,
    contentPadding: PaddingValues,
    onLaunch: (AppInfo) -> Unit,
    menuFor: (AppInfo) -> List<MenuAction>,
    dragCallbacks: DrawerDragCallbacks,
    testTag: String,
    header: (@Composable () -> Unit)? = null,
) {
    val state = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize().testTag(testTag),
    ) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(apps, key = { it.key }) { app ->
            DrawerAppItem(app, iconSize, showLabels, onLaunch, menuFor, dragCallbacks)
        }
    }
}

@Composable
private fun DrawerAppItem(
    app: AppInfo,
    iconSize: Dp,
    showLabels: Boolean,
    onLaunch: (AppInfo) -> Unit,
    menuFor: (AppInfo) -> List<MenuAction>,
    cb: DrawerDragCallbacks,
) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(Offset.Zero) }
    val launch by rememberUpdatedState(onLaunch)
    val callbacks by rememberUpdatedState(cb)
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { position = it.positionInRoot() }
            .pointerInput(app.key) {
                var started = false
                var acc = Offset.Zero
                var downRoot = Offset.Zero
                val threshold = 24.dp.toPx()
                detectLauncherGestures(
                    onTap = { launch(app) },
                    onLongPress = { p ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        started = false; acc = Offset.Zero; downRoot = position + p
                        menu = true
                    },
                    onDrag = { _, d ->
                        acc += d
                        if (!started && acc.getDistance() > threshold) {
                            started = true
                            menu = false
                            callbacks.onStart(app, downRoot + acc)
                        } else if (started) {
                            callbacks.onDrag(d)
                        }
                    },
                    onDragEnd = { cancelled -> if (started) callbacks.onEnd(cancelled) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AppTile(
            icon = { size -> AppIconImage(app.key, size) },
            label = app.label,
            iconSize = iconSize,
            showLabel = showLabels,
            labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth(),
        )
        LauncherMenu(expanded = menu, onDismiss = { menu = false }, actions = menuFor(app), app = app)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryGrid(
    categories: List<CategoryWithApps>,
    columns: Int,
    contentPadding: PaddingValues,
    onOpen: (CategoryWithApps) -> Unit,
    onEdit: (CategoryWithApps) -> Unit,
    onCreate: () -> Unit,
) {
    // Empty categories go last; "Sin clasificar" is only shown when it has apps.
    val shown = remember(categories) {
        val real = categories.filterNot { it.isUnclassified }
        real.filter { it.apps.isNotEmpty() } + real.filter { it.apps.isEmpty() } +
            categories.filter { it.isUnclassified && it.apps.isNotEmpty() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp).testTag("category_grid"),
    ) {
        items(shown, key = { it.id }) { category ->
            CategoryFolderCard(category, onClick = { onOpen(category) }, onLongClick = { onEdit(category) })
        }
        item(key = "new") {
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .combinedClickable(onClick = onCreate)
                    .padding(10.dp)
                    .testTag("new_category"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Add, "Nueva categoría", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(8.dp))
                Text("Nueva", style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryFolderCard(category: CategoryWithApps, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() })
            .padding(6.dp)
            .testTag("category_${category.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (category.apps.isEmpty()) {
                Icon(CategoryIcons.forKey(category.iconKey), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            } else {
                // 2×2 preview of the first apps, like a folder.
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    val cell = (maxWidth - 8.dp) / 2
                    Column(
                        Modifier.align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        category.apps.take(4).chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { AppIconImage(it.key, cell) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(CategoryIcons.forKey(category.iconKey), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (category.apps.size == 1) "1 app" else "${category.apps.size} apps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryDetail(
    category: CategoryWithApps,
    profile: DeviceProfile,
    settings: LauncherSettings,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddApps: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    menuFor: (AppInfo) -> List<MenuAction>,
    dragCallbacks: DrawerDragCallbacks,
) {
    Column(Modifier.fillMaxSize().testTag("category_detail")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("category_back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(CategoryIcons.forKey(category.iconKey), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${category.apps.size} apps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!category.isUnclassified) {
                IconButton(onClick = onAddApps, modifier = Modifier.testTag("category_add_apps")) { Icon(Icons.Outlined.Add, "Añadir apps") }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Editar categoría") }
            }
        }
        if (category.apps.isEmpty()) {
            EmptyState(if (category.isUnclassified) "Todas las apps están clasificadas" else "Esta categoría está vacía. Pulsa + para añadir apps.")
        } else {
            AppGrid(
                apps = category.apps, columns = profile.drawerColumns, iconSize = profile.drawerIconSize,
                showLabels = settings.showDrawerLabels, contentPadding = contentPadding,
                onLaunch = onLaunch, menuFor = menuFor, dragCallbacks = dragCallbacks, testTag = "category_apps",
            )
        }
    }
}

