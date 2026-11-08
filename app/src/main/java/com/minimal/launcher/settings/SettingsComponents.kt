package com.minimal.launcher.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/*
 * Reusable building blocks for every settings screen. New options should be added with
 * [SwitchRow], [ChoiceRow] (a value picked from a list) or [SettingRow] (navigates / runs an action).
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScaffold(title: String, onBack: () -> Unit, testTag: String, content: LazyListScope.() -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection).testTag(testTag),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
    }
}

internal fun LazyListScope.section(title: String) {
    item(key = "h_$title") {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        )
    }
}

/** Plain explanatory text inside a settings list. */
internal fun LazyListScope.note(key: String, text: String) {
    item(key = key) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
internal fun SettingRow(
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    testTag: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = summary?.let { { Text(it) } },
            leadingContent = leading,
            trailingContent = trailing,
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        )
    }
}

@Composable
internal fun SwitchRow(title: String, summary: String?, checked: Boolean, testTag: String, onChange: (Boolean) -> Unit) {
    SettingRow(title, summary, onClick = { onChange(!checked) }, trailing = { Switch(checked = checked, onCheckedChange = onChange) }, testTag = testTag)
}

/** One selectable value of a [ChoiceRow]. */
data class SettingOption<T>(val value: T, val label: String, val summary: String? = null)

/** Row showing the current value; tapping it opens a single-choice dialog. */
@Composable
internal fun <T> ChoiceRow(
    title: String,
    options: List<SettingOption<T>>,
    selected: T,
    testTag: String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, options.firstOrNull { it.value == selected }?.label, onClick = { open = true }, testTag = testTag)
    if (open) {
        OptionsDialog(title, options, selected, onDismiss = { open = false }) { open = false; onSelect(it) }
    }
}

@Composable
internal fun <T> OptionsDialog(
    title: String,
    options: List<SettingOption<T>>,
    selected: T?,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("options_dialog"),
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .selectable(selected = o.value == selected, role = Role.RadioButton) { onSelect(o.value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o.value == selected, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(o.label)
                            o.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/** Confirmation dialog for destructive actions. */
@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
