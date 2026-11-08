package com.minimal.launcher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons available for categories (Material Symbols via material-icons-extended). */
object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "chat" to Icons.Outlined.ChatBubbleOutline,
        "people" to Icons.Outlined.People,
        "work" to Icons.Outlined.Work,
        "finance" to Icons.Outlined.AccountBalance,
        "movie" to Icons.Outlined.Movie,
        "games" to Icons.Outlined.SportsEsports,
        "build" to Icons.Outlined.Build,
        "shopping" to Icons.Outlined.ShoppingBag,
        "map" to Icons.Outlined.Map,
        "camera" to Icons.Outlined.CameraAlt,
        "school" to Icons.Outlined.School,
        "music" to Icons.Outlined.MusicNote,
        "book" to Icons.AutoMirrored.Outlined.MenuBook,
        "health" to Icons.Outlined.FitnessCenter,
        "food" to Icons.Outlined.LocalCafe,
        "home" to Icons.Outlined.Home,
        "star" to Icons.Outlined.Star,
        "favorite" to Icons.Outlined.Favorite,
        "folder" to Icons.Outlined.Folder,
        "apps" to Icons.Outlined.Apps,
    )
    private val map = all.toMap()

    fun forKey(key: String): ImageVector =
        if (key == "unclassified") Icons.AutoMirrored.Outlined.HelpOutline else map[key] ?: Icons.Outlined.Folder
}
