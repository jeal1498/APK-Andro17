package com.minimal.launcher.categories

/**
 * Built-in categories, in drawer order. [key] is stable and used by the local rules; names are user-editable.
 * Keys of categories that existed before (communication, social, …) are kept so manual assignments survive.
 */
enum class BuiltinCategory(val key: String, val displayName: String, val iconKey: String) {
    GOOGLE("google", "Google", "google"),
    AI("ai", "Inteligencia artificial", "ai"),
    DRIVING("driving", "Conducción", "driving"),
    COMMUNICATION("communication", "Comunicación", "chat"),
    SOCIAL("social", "Redes sociales", "people"),
    WORK("work", "Trabajo y productividad", "work"),
    ENTERTAINMENT("entertainment", "Entretenimiento", "movie"),
    FINANCE("finance", "Finanzas personales", "finance"),
    SHOPPING("shopping", "Compras", "shopping"),
    NAVIGATION("navigation", "Transporte y movilidad", "transport"),
    HEALTH("health", "Salud y ejercicio", "health"),
    TOOLS("tools", "Herramientas", "build"),
    EDUCATION("education", "Lectura y educación", "book"),
    ;

    companion object {
        fun fromKey(key: String?): BuiltinCategory? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Previous built-in taxonomy (v1). Used only to migrate existing installations without touching
 * names/icons the user customised. key -> (default name, default icon).
 */
internal val LEGACY_BUILTIN_DEFAULTS: Map<String, Pair<String, String>> = mapOf(
    "communication" to ("Comunicación" to "chat"),
    "social" to ("Social" to "people"),
    "work" to ("Trabajo" to "work"),
    "finance" to ("Finanzas" to "finance"),
    "entertainment" to ("Entretenimiento" to "movie"),
    "games" to ("Juegos" to "games"),
    "tools" to ("Herramientas" to "build"),
    "shopping" to ("Compras" to "shopping"),
    "navigation" to ("Navegación" to "map"),
    "photography" to ("Fotografía" to "camera"),
    "education" to ("Educación" to "school"),
)

/** v1 built-ins that no longer exist (their apps now go to Entretenimiento / Herramientas / Google). */
internal val OBSOLETE_BUILTIN_KEYS = setOf("games", "photography")

/** Bump when the built-in taxonomy changes and existing installations must be migrated. */
const val CATEGORY_TAXONOMY_VERSION = 2

/** Name of the virtual "unclassified" category (always present, cannot be deleted). */
const val UNCLASSIFIED_NAME = "Sin clasificar"
const val UNCLASSIFIED_ID = -1L
