package com.minimal.launcher.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.minimal.launcher.AppContainer
import com.minimal.launcher.categories.CategoryResolver
import com.minimal.launcher.data.prefs.LauncherSettings
import com.minimal.launcher.drawer.AppPickerDialog
import com.minimal.launcher.gestures.GestureAction
import com.minimal.launcher.gestures.HomeGesture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** "Gestos": one row per gesture, each mapped to an action (app and pair actions ask for the target). */
@Composable
internal fun GesturesSettings(s: LauncherSettings, container: AppContainer, scope: CoroutineScope, onBack: () -> Unit) {
    val apps by container.apps.apps.collectAsState()
    val pairs by remember {
        combine(container.apps.apps, container.categories.categories, container.categories.assignments, container.categories.manualApps, container.pairs.pairs) { a, c, asg, m, p ->
            CategoryResolver.group(a, c, asg, m.toSet(), p).flatMap { it.pairs }
        }
    }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<HomeGesture?>(null) }
    var pickingApp by remember { mutableStateOf<HomeGesture?>(null) }
    var pickingPair by remember { mutableStateOf<HomeGesture?>(null) }

    fun save(g: HomeGesture, action: GestureAction) = scope.launch { container.settings.setGesture(g, action) }

    fun describe(a: GestureAction): String = when (a.type) {
        GestureAction.Type.APP -> "Abrir " + (apps.firstOrNull { it.key == a.target }?.label ?: "app no instalada")
        GestureAction.Type.PAIR -> "Abrir " + (pairs.firstOrNull { it.id.toString() == a.target }?.label ?: "par eliminado")
        else -> a.type.label
    }

    SettingsScaffold("Gestos", onBack, "settings_gestures") {
        note("info", "Funcionan en espacios vacíos de la pantalla de inicio. No interfieren con los gestos de navegación de Android (bordes y barra inferior), con el cambio de página ni con mover iconos.")
        HomeGesture.entries.forEach { g ->
            item(key = g.name) { SettingRow(g.label, describe(s.gesture(g)), onClick = { editing = g }, testTag = "gesture_${g.name}") }
        }
        note("a11y", "«Apagar la pantalla» necesita el servicio de Accesibilidad (Android 9 o superior).")
    }

    editing?.let { g ->
        val types = GestureAction.Type.entries.filter { it != GestureAction.Type.HOME_MENU || g == HomeGesture.LONG_PRESS || s.gesture(g).type == it }
        OptionsDialog(
            g.label,
            types.map { SettingOption(it, it.label) },
            s.gesture(g).type,
            onDismiss = { editing = null },
        ) { type ->
            editing = null
            when (type) {
                GestureAction.Type.APP -> pickingApp = g
                GestureAction.Type.PAIR -> pickingPair = g
                else -> save(g, GestureAction(type))
            }
        }
    }
    pickingApp?.let { g ->
        AppPickerDialog(
            title = "App para «${g.label}»", apps = apps, maxSelection = 1, confirmLabel = "Elegir",
            onDismiss = { pickingApp = null },
            onConfirm = { picked -> pickingApp = null; picked.firstOrNull()?.let { save(g, GestureAction(GestureAction.Type.APP, it.key)) } },
        )
    }
    pickingPair?.let { g ->
        if (pairs.isEmpty()) {
            ConfirmDialog("No hay pares", "Crea uno manteniendo pulsada una app y eligiendo «Combinar en par».", "Entendido", onDismiss = { pickingPair = null }) {}
        } else {
            OptionsDialog("Par para «${g.label}»", pairs.map { SettingOption(it.id.toString(), it.label) }, s.gesture(g).target, onDismiss = { pickingPair = null }) { id ->
                pickingPair = null
                save(g, GestureAction(GestureAction.Type.PAIR, id))
            }
        }
    }
}
