package com.drivecinema.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.icons.AppIcons

/** A banner's background and icon colour ([DcBannerKind] is shared with the desktop). */
fun dcBannerColors(kind: DcBannerKind): Pair<Color, Color> = when (kind) {
    DcBannerKind.Info -> DcColors.Surface2 to DcColors.Text2
    DcBannerKind.Warning -> DcColors.WarningContainer to DcColors.Warning
    DcBannerKind.Error -> DcColors.DangerContainer to DcColors.Danger
}

/** A note in the flow of a screen: an icon and text that says what to do. */
@Composable
fun DcBanner(text: String, modifier: Modifier = Modifier, kind: DcBannerKind = DcBannerKind.Info) {
    val (container, iconColor) = dcBannerColors(kind)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(DcTokens.Radius.CARD.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            if (kind == DcBannerKind.Info) AppIcons.Info else AppIcons.Warning,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Text(text, style = DcTheme.type.body, color = DcColors.Text1, modifier = Modifier.weight(1f))
    }
}

/** A status chip's background and text colour ([DcStatusKind] is shared with the desktop). */
fun dcStatusColors(kind: DcStatusKind): Pair<Color, Color> = when (kind) {
    DcStatusKind.Success -> DcColors.Surface2 to DcColors.Success
    DcStatusKind.Warning -> DcColors.Surface2 to DcColors.Warning
    DcStatusKind.Neutral -> DcColors.Surface2 to DcColors.Text2
    DcStatusKind.Brand -> DcColors.BrandContainer to DcColors.Text1
}

/** A small pill with a dot: "Connected at home", "Not reachable", "Uploading 42 %". */
@Composable
fun DcStatusChip(text: String, modifier: Modifier = Modifier, kind: DcStatusKind = DcStatusKind.Neutral) {
    val (container, content) = dcStatusColors(kind)
    Row(
        modifier = modifier
            .background(container, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (kind != DcStatusKind.Brand) {
            Box(Modifier.size(7.dp).background(content, CircleShape))
        }
        Text(
            text,
            style = DcTheme.type.caption.copy(fontWeight = FontWeight.Medium),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The phone's top bar: Back and the screen's title (design §3.4), below the status bar. */
@Composable
fun DcTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DcIconButton(AppIcons.ArrowBack, contentDescription = "Back", onClick = onBack)
        Text(
            title,
            style = DcTheme.type.headline,
            color = DcColors.Text1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
