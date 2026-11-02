package com.drivecinema.app.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.setup.GuideIcon
import com.drivecinema.app.ui.setup.GuideIllustration
import com.drivecinema.app.ui.setup.GuideSection
import com.drivecinema.app.ui.setup.SetupGuideContent
import com.drivecinema.app.ui.setup.shownAs

/** Task P.4: a topic's picture, drawn from `:shared` data (no image files); nothing when the topic has none. */
@Composable
fun HelpIllustration(topic: GuideSection, modifier: Modifier = Modifier) {
    val illustration = topic.illustration ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DcColors.Canvas, RoundedCornerShape(DcTokens.Radius.CARD.dp))
            .padding(16.dp)
            .testTag("help_illustration"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (illustration) {
            GuideIllustration.Folders -> FolderTree()
            GuideIllustration.FileNames -> FileNames(topic)
            is GuideIllustration.Flow -> Flow(illustration)
        }
    }
}

/** The library layout, one row per folder or file, indented by level. */
@Composable
private fun FolderTree() {
    SetupGuideContent.folderTreeLines.forEach { line ->
        Row(
            modifier = Modifier.padding(start = (line.depth * 18).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (line.isFolder) AppIcons.Folder else AppIcons.Movie,
                contentDescription = null,
                tint = if (line.isFolder) DcColors.RatingStar else DcColors.Text3,
                modifier = Modifier.size(18.dp)
            )
            Text(line.name, style = DcTheme.type.caption, color = if (line.isFolder) DcColors.Text1 else DcColors.Text2)
        }
    }
}

/** Each example file name and what DriveCinema shows for it (checked against the parser in `SetupGuideContentTest`). */
@Composable
private fun FileNames(topic: GuideSection) {
    Text("File name → what DriveCinema shows", style = DcTheme.type.caption, color = DcColors.Text3)
    topic.examples.forEach { example ->
        Column(Modifier.padding(top = 6.dp)) {
            Text(example.fileName, style = DcTheme.type.caption, color = DcColors.Text1, fontFamily = FontFamily.Monospace)
            Text("→ ${example.shownAs}", style = DcTheme.type.body, color = DcColors.Success)
        }
    }
}

/** Devices or places left to right, an arrow between each two, and a caption below. */
@Composable
private fun Flow(flow: GuideIllustration.Flow) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        flow.nodes.forEachIndexed { index, node ->
            if (index > 0) {
                Icon(
                    AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = DcColors.Text3,
                    modifier = Modifier.padding(top = 12.dp).size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(44.dp).background(DcColors.Surface3, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(node.icon.vector, contentDescription = null, tint = DcColors.Text1, modifier = Modifier.size(24.dp))
                }
                Text(node.label, style = DcTheme.type.caption, color = DcColors.Text2, textAlign = TextAlign.Center)
            }
        }
    }
    flow.caption?.let {
        Text(
            it,
            style = DcTheme.type.caption,
            color = DcColors.Text3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
    }
}

private val GuideIcon.vector: ImageVector
    get() = when (this) {
        GuideIcon.PHONE -> AppIcons.Smartphone
        GuideIcon.TV -> AppIcons.Tv
        GuideIcon.PC -> AppIcons.DesktopWindows
        GuideIcon.WIFI -> AppIcons.Wifi
        GuideIcon.VPN -> AppIcons.VpnKey
        GuideIcon.CLOUD -> AppIcons.CloudUpload
        GuideIcon.FOLDER -> AppIcons.Folder
        GuideIcon.LOCK -> AppIcons.Lock
    }

/** The icon of a topic in the Help home's list and the TV's left pane. */
internal fun helpIcon(id: String): ImageVector = when (id) {
    SetupGuideContent.FOLDERS -> AppIcons.Folder
    SetupGuideContent.FILM_NAMES -> AppIcons.Movie
    SetupGuideContent.EPISODE_NAMES -> AppIcons.VideoLibrary
    SetupGuideContent.FORMATS -> AppIcons.Subtitles
    SetupGuideContent.SKIP_INTRO -> AppIcons.FastForward
    SetupGuideContent.TV_SIGN_IN -> AppIcons.Tv
    SetupGuideContent.DEVICE_FOLDERS, SetupGuideContent.COMPUTER_FOLDERS -> AppIcons.Storage
    SetupGuideContent.PC_STREAMING -> AppIcons.DesktopWindows
    SetupGuideContent.IMPORT -> AppIcons.CloudUpload
    SetupGuideContent.SHARE -> AppIcons.Group
    SetupGuideContent.PAIRED_ACCESS -> AppIcons.Lock
    SetupGuideContent.ABOUT -> AppIcons.Info
    SetupGuideContent.PROBLEMS -> AppIcons.Warning
    else -> AppIcons.HelpOutline
}
