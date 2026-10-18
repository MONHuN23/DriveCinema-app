package com.drivecinema.app.ui.mobile.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class MobileTab(val label: String, val icon: String) {
    HOME("Home", "🏠"),
    SEARCH("Search", "🔍"),
    LIBRARY("Library", "📚")
}

@Composable
fun MobileBottomNav(
    selectedTab: MobileTab,
    onTabSelected: (MobileTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = Color(0xFF16181D),
        contentColor = Color(0xFFF0F2F5)
    ) {
        MobileTab.values().forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Text(tab.icon) },
                label = { Text(tab.label, color = if (selectedTab == tab) Color.White else Color.Gray) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color(0xFFE50914).copy(alpha = 0.2f)
                )
            )
        }
    }
}
