package com.minimal.launcher.pairs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.drawer.AppPickerDialog
import com.minimal.launcher.ui.components.AppIconImage

/** Combined icon: 2 apps on a diagonal, or 3 in a triangle, on a rounded tile. */
@Composable
fun PairIcon(apps: List<AppInfo>, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .testTag("pair_icon"),
    ) {
        val small = if (apps.size >= 3) size * 0.46f else size * 0.56f
        val pad = size * 0.06f
        when {
            apps.size >= 3 -> {
                AppIconImage(apps[0].key, small, Modifier.align(Alignment.TopCenter).padding(top = pad))
                AppIconImage(apps[1].key, small, Modifier.align(Alignment.BottomStart).padding(start = pad, bottom = pad))
                AppIconImage(apps[2].key, small, Modifier.align(Alignment.BottomEnd).padding(end = pad, bottom = pad))
            }
            apps.size == 2 -> {
                AppIconImage(apps[0].key, small, Modifier.align(Alignment.TopStart).padding(pad))
                AppIconImage(apps[1].key, small, Modifier.align(Alignment.BottomEnd).padding(pad))
            }
        }
        Icon(
            Icons.Outlined.VerticalSplit, null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.align(if (apps.size >= 3) Alignment.Center else Alignment.BottomStart).padding(pad).size(size * 0.22f),
        )
    }
}

/** "Combinar en par": pick 1 app (pair) or 2 apps (trio, the third opens as a floating window). */
@Composable
fun PairPickerDialog(first: AppInfo, apps: List<AppInfo>, onDismiss: () -> Unit, onCreate: (List<String>) -> Unit) {
    AppPickerDialog(
        title = "Combinar ${first.label} con…",
        subtitle = "Elige 1 app para pantalla dividida, o 2 (la tercera se abre como ventana flotante si tu dispositivo lo permite).",
        apps = apps.filter { it.key != first.key },
        maxSelection = 2,
        confirmLabel = "Crear par",
        onDismiss = onDismiss,
        onConfirm = { onCreate(listOf(first.key) + it.map { a -> a.key }) },
    )
}

/** Guided, one-time explanation before sending the user to the accessibility settings. */
@Composable
fun AccessibilityDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("accessibility_dialog"),
        icon = { Icon(Icons.Outlined.VerticalSplit, null) },
        title = { Text("Activar pantalla dividida y gestos") },
        text = {
            Column {
                Text("Android solo permite activar la pantalla dividida, apagar la pantalla o abrir las notificaciones mediante un servicio de Accesibilidad.")
                Text("1. Toca «Abrir ajustes».\n2. Busca «Minimal Launcher: pantalla dividida y gestos».\n3. Actívalo y acepta.", Modifier.padding(top = 12.dp))
                Text(
                    "No lee ni guarda lo que aparece en pantalla. Si Android dice «Ajuste restringido» (APK instalado a mano): Ajustes › Apps › Minimal Launcher › ⋮ › Permitir ajustes restringidos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onOpenSettings() }) { Text("Abrir ajustes") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } },
    )
}
