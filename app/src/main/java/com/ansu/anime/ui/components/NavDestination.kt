package com.ansu.anime.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search

enum class NavDestination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Search("Search", Icons.Filled.Search),
    Schedule("Schedule", Icons.Filled.Schedule),
    Downloads("Downloads", Icons.Filled.Download),
    MySpace("My Space", Icons.Filled.Person),
}
