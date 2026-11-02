package com.drivecinema.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Task L.2: tooltip text for a control, with its keyboard shortcut when it has one: "Next episode (N)". */
fun tooltipText(label: String, shortcut: String? = null): String =
    if (shortcut.isNullOrBlank()) label else "$label ($shortcut)"

/** Shows [text] in a small dark bubble above [content] after a short hover. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithTooltip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Text(
                text,
                color = DesktopColors.TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(Color(0xF02A2D35), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        },
        delayMillis = 400,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.TopCenter,
            alignment = Alignment.TopCenter,
            offset = DpOffset(0.dp, (-6).dp)
        ),
        modifier = modifier,
        content = content
    )
}

/**
 * Round, borderless icon button with a hover highlight and a tooltip. [tooltip] is also the screen-reader
 * description, so every icon-only button has a name.
 */
@Composable
fun IconActionButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 26.dp,
    tint: Color = Color.White,
    background: Color = Color.Transparent,
    hoverBackground: Color = Color(0x2EFFFFFF),
    iconContent: @Composable () -> Unit = { Icon(icon, contentDescription = tooltip, tint = tint, modifier = Modifier.size(iconSize)) }
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    WithTooltip(tooltip, modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (hovered) hoverBackground else background)
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick)
        ) { iconContent() }
    }
}

/** "Back 10 s" / "forward 10 s": the replay arrow (mirrored for forward) with the seconds inside. */
@Composable
fun SeekIcon(forward: Boolean, seconds: Int, contentDescription: String, size: Dp = 28.dp, tint: Color = Color.White) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Icon(
            DesktopIcons.Replay,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size).graphicsLayer { if (forward) scaleX = -1f }
        )
        // The arrow's circle is centred a little below the middle of the 24×24 icon (y = 13).
        Text(
            seconds.toString(),
            color = tint,
            fontSize = (size.value * 0.30f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = size / 24)
        )
    }
}

/** The back button of every full-page screen: arrow + "Back", Esc in the tooltip. */
@Composable
fun BackButton(onClick: () -> Unit, containerColor: Color = Color.Transparent) {
    WithTooltip(tooltipText("Back", "Esc")) {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = containerColor),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Icon(DesktopIcons.ArrowBack, contentDescription = null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Back", color = DesktopColors.TextPrimary)
        }
    }
}

/** Icon + label for the inside of a Material button: `Button { ButtonLabel(DesktopIcons.PlayArrow, "Play") }`. */
@Composable
fun ButtonLabel(icon: ImageVector, label: String, color: Color = Color.Unspecified, bold: Boolean = false) {
    Icon(icon, contentDescription = null, tint = color.takeOrElse { LocalContentColor.current }, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(label, color = color, fontWeight = if (bold) FontWeight.Bold else null)
}
