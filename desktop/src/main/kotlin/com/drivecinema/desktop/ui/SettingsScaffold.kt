package com.drivecinema.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.desktop.DesktopScreen
import com.drivecinema.desktop.ui.design.DesktopSideItem

/** Task P.6 (decision D-P3): the pages of the desktop's settings shell, in the side list's order. */
enum class SettingsPage(val screen: DesktopScreen, val label: String) {
    LOCAL_FOLDERS(DesktopScreen.LocalFolders, "Local folders"),
    IMPORT(DesktopScreen.Import, "Import to Drive"),
    SHARING(DesktopScreen.Sharing, "Share with my devices"),
    PCS(DesktopScreen.Pcs, "Play from another PC"),
    /** Task R.3. */
    NAMING_HELPER(DesktopScreen.NamingHelper, SettingsLabels.NAMING_HELPER);

    val icon: ImageVector
        get() = when (this) {
            LOCAL_FOLDERS -> DesktopIcons.Folder
            IMPORT -> DesktopIcons.CloudUpload
            SHARING -> DesktopIcons.WifiTethering
            PCS -> DesktopIcons.DesktopWindows
            NAMING_HELPER -> DesktopIcons.Article
        }
}

/** What the shell's side list does: open a page in place of the current one, or go back to the library. */
class SettingsShellActions(
    val open: (DesktopScreen) -> Unit,
    val back: () -> Unit,
    /** Import to Drive needs a library (as in the account menu); greyed out before. */
    val importAvailable: Boolean
)

/**
 * Task P.6 (D-P3): every page the account menu opens, in one shell: a 236 dp side list (Back, the pages, the Help
 * Center below) and the page at most 720 dp wide. The side list swaps pages ([DesktopNavigator.openSettingsPage]),
 * so one Esc returns to the library.
 */
@Composable
fun SettingsScaffold(page: SettingsPage, actions: SettingsShellActions, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxSize().background(DesktopColors.Background)) {
        Column(
            Modifier
                .width(236.dp)
                .fillMaxHeight()
                .background(DesktopColors.Surface)
                .padding(horizontal = 12.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BackButton(actions.back)
            Spacer(Modifier.height(18.dp))
            SettingsPage.entries.forEach { entry ->
                DesktopSideItem(
                    entry.label,
                    isSelected = entry == page,
                    onClick = { actions.open(entry.screen) },
                    icon = entry.icon,
                    enabled = entry != SettingsPage.IMPORT || actions.importAvailable
                )
            }
            Spacer(Modifier.weight(1f))
            DesktopSideItem(
                SettingsLabels.HELP_CENTER,
                isSelected = false,
                onClick = { actions.open(DesktopScreen.SetupGuide()) },
                icon = DesktopIcons.HelpOutline
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            key(page) {
                val scroll = rememberScrollState()
                Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 40.dp, vertical = 36.dp), content = content)
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

/** A page's headline and one-paragraph description, with its one primary action top right (design §5). */
@Composable
fun PageHeader(title: String, description: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = DesktopType.display, color = DesktopColors.TextPrimary)
            Text(description, style = DesktopType.body, color = DesktopColors.TextSecondary)
        }
        action?.invoke()
    }
}
