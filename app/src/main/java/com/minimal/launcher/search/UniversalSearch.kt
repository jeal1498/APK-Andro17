package com.minimal.launcher.search

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.minimal.launcher.categories.Classifier

/** A contact phone number found by [ContactsSearch]. */
data class Contact(val id: Long, val name: String, val phone: String)

/** Non-app search results. [intent] is what runs when the row is tapped (null = handled by the UI). */
sealed interface SearchItem {
    val title: String
    val subtitle: String?

    data class SystemSetting(override val title: String, override val subtitle: String?, val action: String) : SearchItem
    data class ContactItem(val contact: Contact) : SearchItem {
        override val title get() = contact.name
        override val subtitle get() = contact.phone
    }
    data class QuickAction(override val title: String, override val subtitle: String?, val kind: Kind, val value: String) : SearchItem {
        enum class Kind { CALL, MESSAGE, WEB, FLASHLIGHT }
    }
}

/**
 * Universal search, all local: system settings, quick actions ("llamar a…", "mensaje a…", "buscar…",
 * "linterna") and contacts (only when the user granted the optional permission).
 */
object UniversalSearch {

    private data class SettingEntry(val title: String, val action: String, val words: List<String>)

    private val settings = listOf(
        SettingEntry("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wi-fi", "internet", "red", "redes")),
        SettingEntry("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "audifonos", "auriculares", "vincular")),
        SettingEntry("Datos móviles", Settings.ACTION_DATA_ROAMING_SETTINGS, listOf("datos", "movil", "roaming", "itinerancia", "sim")),
        SettingEntry("Modo avión", Settings.ACTION_AIRPLANE_MODE_SETTINGS, listOf("avion", "airplane", "vuelo")),
        SettingEntry("Pantalla y brillo", Settings.ACTION_DISPLAY_SETTINGS, listOf("pantalla", "brillo", "display", "modo oscuro", "tema")),
        SettingEntry("Sonido y vibración", Settings.ACTION_SOUND_SETTINGS, listOf("sonido", "volumen", "tono", "vibracion", "silencio")),
        SettingEntry("Batería", Intent.ACTION_POWER_USAGE_SUMMARY, listOf("bateria", "energia", "ahorro", "carga")),
        SettingEntry("Almacenamiento", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("almacenamiento", "espacio", "memoria", "storage")),
        SettingEntry("Aplicaciones", Settings.ACTION_APPLICATION_SETTINGS, listOf("aplicaciones", "apps", "permisos")),
        SettingEntry("Notificaciones", "android.settings.NOTIFICATION_SETTINGS", listOf("notificaciones", "avisos", "no molestar")),
        SettingEntry("Ubicación", Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("ubicacion", "gps", "localizacion")),
        SettingEntry("Seguridad", Settings.ACTION_SECURITY_SETTINGS, listOf("seguridad", "bloqueo", "huella", "contrasena", "pin")),
        SettingEntry("Privacidad", Settings.ACTION_PRIVACY_SETTINGS, listOf("privacidad")),
        SettingEntry("Accesibilidad", Settings.ACTION_ACCESSIBILITY_SETTINGS, listOf("accesibilidad")),
        SettingEntry("Fecha y hora", Settings.ACTION_DATE_SETTINGS, listOf("fecha", "hora", "zona horaria", "reloj")),
        SettingEntry("Idioma y teclado", Settings.ACTION_LOCALE_SETTINGS, listOf("idioma", "lenguaje", "teclado", "language")),
        SettingEntry("NFC", Settings.ACTION_NFC_SETTINGS, listOf("nfc", "pago", "contactless")),
        SettingEntry("Zona Wi-Fi / compartir conexión", "android.settings.TETHER_SETTINGS", listOf("hotspot", "compartir", "zona", "anclaje")),
        SettingEntry("Cuentas", Settings.ACTION_SYNC_SETTINGS, listOf("cuentas", "sincronizacion", "google")),
        SettingEntry("Opciones de desarrollador", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("desarrollador", "developer", "depuracion", "usb")),
        SettingEntry("Información del teléfono", Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("informacion", "telefono", "version", "android", "acerca")),
        SettingEntry("Ajustes del sistema", Settings.ACTION_SETTINGS, listOf("ajustes", "configuracion", "settings")),
    )

    private val callWords = listOf("llamar a ", "llamar ", "marcar a ", "marcar ", "call ")
    private val messageWords = listOf("mensaje a ", "mensaje ", "sms a ", "sms ", "escribir a ", "text ")
    private val webWords = listOf("buscar en la web ", "buscar en internet ", "buscar ", "google ", "search ")

    fun matchSettings(query: String): List<SearchItem.SystemSetting> {
        val q = Classifier.normalize(query.trim())
        if (q.length < 2) return emptyList()
        return settings.mapNotNull { e ->
            val title = Classifier.normalize(e.title)
            val rank = when {
                title.startsWith(q) -> 0
                e.words.any { it.startsWith(q) } -> 1
                q.length >= 3 && (title.contains(q) || e.words.any { it.contains(q) }) -> 2
                else -> return@mapNotNull null
            }
            rank to SearchItem.SystemSetting(e.title, "Ajustes", e.action)
        }.sortedBy { it.first }.map { it.second }.take(4)
    }

    /** Text after a command prefix ("llamar a ana" → "ana"), or null. */
    fun stripCommand(query: String, prefixes: List<String>): String? {
        val q = Classifier.normalize(query).trimStart()
        val p = prefixes.firstOrNull { q.startsWith(it) } ?: return null
        return query.trimStart().drop(p.length).trim().ifEmpty { null }
    }

    /** Text to look up in contacts: the name after "llamar a"/"mensaje a", or the whole query. */
    fun contactQuery(query: String): String = stripCommand(query, callWords) ?: stripCommand(query, messageWords) ?: query.trim()

    private fun isPhoneNumber(s: String) = s.count { it.isDigit() } >= 3 && s.all { it.isDigit() || it in "+-() " }

    fun search(query: String, contacts: List<Contact>): List<SearchItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<SearchItem>()
        val callTarget = stripCommand(q, callWords)
        val messageTarget = stripCommand(q, messageWords)
        val normalized = Classifier.normalize(q)

        when {
            callTarget != null -> {
                if (isPhoneNumber(callTarget)) out += SearchItem.QuickAction("Llamar a $callTarget", "Teléfono", SearchItem.QuickAction.Kind.CALL, callTarget)
                contacts.take(3).forEach { out += SearchItem.QuickAction("Llamar a ${it.name}", it.phone, SearchItem.QuickAction.Kind.CALL, it.phone) }
            }
            messageTarget != null -> {
                if (isPhoneNumber(messageTarget)) out += SearchItem.QuickAction("Mensaje a $messageTarget", "SMS", SearchItem.QuickAction.Kind.MESSAGE, messageTarget)
                contacts.take(3).forEach { out += SearchItem.QuickAction("Mensaje a ${it.name}", it.phone, SearchItem.QuickAction.Kind.MESSAGE, it.phone) }
            }
            isPhoneNumber(q) -> {
                out += SearchItem.QuickAction("Llamar a $q", "Teléfono", SearchItem.QuickAction.Kind.CALL, q)
                out += SearchItem.QuickAction("Mensaje a $q", "SMS", SearchItem.QuickAction.Kind.MESSAGE, q)
            }
            else -> contacts.take(5).forEach { out += SearchItem.ContactItem(it) }
        }
        if (normalized.startsWith("linterna") || normalized.startsWith("flash") || normalized.startsWith("lampara")) {
            out += SearchItem.QuickAction("Encender o apagar la linterna", null, SearchItem.QuickAction.Kind.FLASHLIGHT, "")
        }
        out += matchSettings(q)
        val web = stripCommand(q, webWords) ?: q
        out += SearchItem.QuickAction("Buscar «$web» en la web", null, SearchItem.QuickAction.Kind.WEB, web)
        return out
    }

    fun intentFor(item: SearchItem): Intent? = when (item) {
        is SearchItem.SystemSetting -> Intent(item.action)
        is SearchItem.ContactItem -> Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_URI, item.contact.id.toString()))
        is SearchItem.QuickAction -> when (item.kind) {
            SearchItem.QuickAction.Kind.CALL -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", item.value, null))
            SearchItem.QuickAction.Kind.MESSAGE -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", item.value, null))
            SearchItem.QuickAction.Kind.WEB -> Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, item.value)
            SearchItem.QuickAction.Kind.FLASHLIGHT -> null
        }
    }

    /** Browser fallback when no app handles ACTION_WEB_SEARCH. */
    fun webFallback(query: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
}
