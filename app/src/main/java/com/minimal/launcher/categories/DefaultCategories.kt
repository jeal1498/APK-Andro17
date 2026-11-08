package com.minimal.launcher.categories

/** Built-in categories. [key] is stable and used by the local rules; names are user-editable. */
enum class BuiltinCategory(val key: String, val displayName: String, val iconKey: String) {
    COMMUNICATION("communication", "Comunicación", "chat"),
    SOCIAL("social", "Social", "people"),
    WORK("work", "Trabajo", "work"),
    FINANCE("finance", "Finanzas", "finance"),
    ENTERTAINMENT("entertainment", "Entretenimiento", "movie"),
    GAMES("games", "Juegos", "games"),
    TOOLS("tools", "Herramientas", "build"),
    SHOPPING("shopping", "Compras", "shopping"),
    NAVIGATION("navigation", "Navegación", "map"),
    PHOTOGRAPHY("photography", "Fotografía", "camera"),
    EDUCATION("education", "Educación", "school"),
    ;

    companion object {
        fun fromKey(key: String?): BuiltinCategory? = entries.firstOrNull { it.key == key }
    }
}

/** Name of the virtual "unclassified" category (always present, cannot be deleted). */
const val UNCLASSIFIED_NAME = "Sin clasificar"
const val UNCLASSIFIED_ID = -1L
