package com.drivecinema.app.ui.mobile.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.icons.AppIcons

/** [selectedIcon] is filled; [icon] (outlined where Material has one) marks the tabs that aren't selected. */
enum class MobileTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Home", AppIcons.HomeOutlined, AppIcons.Home),
    SEARCH("Search", AppIcons.Search, AppIcons.Search),
    LIBRARY("Library", AppIcons.VideoLibraryOutlined, AppIcons.VideoLibrary)
}

/**
 * The phone's bottom bar, streaming-app style: 64 dp on the screen background with a hairline on top, no pill. The
 * selected tab is a filled icon and label in [DcColors.Text1]; the others are outlined in [DcColors.Text3].
 */
@Composable
fun MobileBottomNav(
    selectedTab: MobileTab,
    onTabSelected: (MobileTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DcColors.Canvas.copy(alpha = 0.96f))
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DcColors.Surface2))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .selectableGroup()
        ) {
            MobileTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val color = if (selected) DcColors.Text1 else DcColors.Text3
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(tab) })
                        .testTag("mobile_tab_${tab.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
                ) {
                    Icon(
                        if (selected) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        tab.label,
                        style = DcTheme.type.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = color
                    )
                }
            }
        }
    }
}
