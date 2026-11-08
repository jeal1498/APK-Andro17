package com.minimal.launcher.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Contacts, settings and quick actions below the app results. */
@Composable
fun UniversalResults(
    items: List<SearchItem>,
    askContacts: Boolean,
    onAskContacts: () -> Unit,
    onDismissContacts: () -> Unit,
    onClick: (SearchItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("universal_results")) {
        items.forEach { item -> Row(item.title, item.subtitle, iconFor(item), "result_${item.title}") { onClick(item) } }
        if (askContacts) {
            Row("Buscar también en contactos", "Opcional: se pide permiso una vez; nada sale del teléfono", Icons.Outlined.Contacts, "ask_contacts", trailing = {
                IconButton(onClick = onDismissContacts) { Icon(Icons.Outlined.Close, "No, gracias") }
            }, onClick = onAskContacts)
        }
    }
}

private fun iconFor(item: SearchItem): ImageVector = when (item) {
    is SearchItem.SystemSetting -> Icons.Outlined.Settings
    is SearchItem.ContactItem -> Icons.Outlined.Person
    is SearchItem.QuickAction -> when (item.kind) {
        SearchItem.QuickAction.Kind.CALL -> Icons.Outlined.Call
        SearchItem.QuickAction.Kind.MESSAGE -> Icons.Outlined.Sms
        SearchItem.QuickAction.Kind.WEB -> Icons.Outlined.Language
        SearchItem.QuickAction.Kind.FLASHLIGHT -> Icons.Outlined.FlashlightOn
    }
}

@Composable
private fun Row(title: String, subtitle: String?, icon: ImageVector, tag: String, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).testTag(tag),
    )
}
