package com.drivecinema.desktop.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcStatusKind
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.desktop.ui.DesktopColors
import com.drivecinema.desktop.ui.DesktopIcons
import com.drivecinema.desktop.ui.DesktopType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/** A desktop control's fill and content colour in its current state. */
@Immutable
data class DesktopControlColors(val container: Color, val content: Color)

/**
 * Milestone P: a desktop button's colours (design §3.4–3.5). Hovering lifts it one surface step; a disabled filled
 * button keeps a quiet shape and its content at 38 %.
 */
fun desktopButtonColors(kind: DcButtonKind, hovered: Boolean, enabled: Boolean = true): DesktopControlColors {
    if (!enabled) {
        val filled = kind == DcButtonKind.Primary || kind == DcButtonKind.Secondary
        return DesktopControlColors(
            container = if (filled) DesktopColors.Surface else Color.Transparent,
            content = DesktopColors.TextPrimary.copy(alpha = DcTokens.DISABLED_ALPHA)
        )
    }
    return when (kind) {
        DcButtonKind.Primary -> DesktopControlColors(
            if (hovered) lerp(DesktopColors.Accent, Color.White, 0.08f) else DesktopColors.Accent,
            DesktopColors.OnAccent
        )
        DcButtonKind.Secondary -> DesktopControlColors(
            if (hovered) DesktopColors.SurfaceSelected else DesktopColors.SurfaceRaised,
            DesktopColors.TextPrimary
        )
        DcButtonKind.Tertiary -> DesktopControlColors(
            if (hovered) DesktopColors.SurfaceRaised else Color.Transparent,
            if (hovered) DesktopColors.TextPrimary else DesktopColors.TextSecondary
        )
        DcButtonKind.Destructive -> DesktopControlColors(
            if (hovered) DesktopColors.DangerContainer else Color.Transparent,
            DesktopColors.Danger
        )
    }
}

/** A banner's background and icon colour; information is never brand red. */
fun desktopBannerColors(kind: DcBannerKind): DesktopControlColors = when (kind) {
    DcBannerKind.Info -> DesktopControlColors(DesktopColors.SurfaceRaised, DesktopColors.TextSecondary)
    DcBannerKind.Warning -> DesktopControlColors(DesktopColors.WarningContainer, DesktopColors.Warning)
    DcBannerKind.Error -> DesktopControlColors(DesktopColors.DangerContainer, DesktopColors.Danger)
}

/** A status chip's background and text colour. */
fun desktopStatusColors(kind: DcStatusKind): DesktopControlColors = when (kind) {
    DcStatusKind.Success -> DesktopControlColors(DesktopColors.SurfaceRaised, DesktopColors.Success)
    DcStatusKind.Warning -> DesktopControlColors(DesktopColors.SurfaceRaised, DesktopColors.Warning)
    DcStatusKind.Neutral -> DesktopControlColors(DesktopColors.SurfaceRaised, DesktopColors.TextSecondary)
    DcStatusKind.Brand -> DesktopControlColors(DesktopColors.AccentContainer, DesktopColors.TextPrimary)
}

/** Keyboard focus: a white ring with a small gap around the control (design §3.5). */
fun Modifier.desktopFocusRing(focused: Boolean, cornerRadius: Dp): Modifier = drawWithContent {
    drawContent()
    if (focused) {
        val ring = DcTokens.Focus.RING_WIDTH.dp.toPx()
        val outset = DcTokens.Focus.RING_GAP.dp.toPx() + ring / 2
        drawRoundRect(
            color = DesktopColors.FocusRing,
            topLeft = Offset(-outset, -outset),
            size = Size(size.width + 2 * outset, size.height + 2 * outset),
            cornerRadius = CornerRadius(cornerRadius.toPx() + outset),
            style = Stroke(ring)
        )
    }
}

/** Milestone P's desktop button: hover, a keyboard focus ring; while [loading] it ignores clicks. */
@Composable
fun DesktopButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: DcButtonKind = DcButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    small: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = desktopButtonColors(kind, hovered, enabled)
    val radius = DcTokens.Radius.CONTROL.dp
    val textOnly = kind == DcButtonKind.Tertiary || kind == DcButtonKind.Destructive
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = (if (small) DcTokens.DESKTOP.smallButtonHeight else DcTokens.DESKTOP.buttonHeight).dp)
            .desktopFocusRing(focused, radius)
            .background(colors.container, RoundedCornerShape(radius))
            .hoverable(interactionSource, enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = { if (!loading) onClick() }
            )
            .padding(horizontal = if (textOnly) 10.dp else if (small) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (loading) {
            CircularProgressIndicator(color = colors.content, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
        }
        Text(text, style = DesktopType.label, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A group of rows on Surface 1; separate rows with [DesktopGroupDivider]. */
@Composable
fun DesktopGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DesktopColors.Surface, RoundedCornerShape(DcTokens.Radius.CARD.dp)),
        content = content
    )
}

@Composable
fun DesktopGroupDivider(startInset: Dp = 16.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(1.dp)
            .background(DesktopColors.Divider)
    )
}

/**
 * A row in a [DesktopGroup]: optional icon, title, optional supporting text, trailing actions. With [onClick] the
 * whole row is clickable and lights up on hover.
 */
@Composable
fun DesktopListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val radius = DcTokens.Radius.CARD.dp
    val clickModifier = if (onClick != null) {
        Modifier
            .desktopFocusRing(focused, radius)
            .background(if (hovered) DesktopColors.SurfaceRaised else Color.Transparent, RoundedCornerShape(radius))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .then(clickModifier)
            .fillMaxWidth()
            .heightIn(min = DcTokens.DESKTOP.rowMinHeight.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Box(
                Modifier.size(36.dp).background(DesktopColors.SurfaceRaised, RoundedCornerShape(DcTokens.Radius.CONTROL.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = DesktopType.rowTitle, color = DesktopColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = DesktopType.caption, color = DesktopColors.TextSecondary)
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
        }
    }
}

/**
 * A row in a single-choice menu (P.8: the player's audio and subtitle tracks): a check in front of the chosen one on
 * Surface 3; hover one step up; the keyboard focus ring. Role RadioButton.
 */
@Composable
fun DesktopChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val radius = DcTokens.Radius.CONTROL.dp
    val container = when {
        hovered -> DesktopColors.SurfaceRaised
        selected -> DesktopColors.SurfaceSelected
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .desktopFocusRing(focused, radius)
            .background(container, RoundedCornerShape(radius))
            .hoverable(interactionSource)
            .selectable(selected = selected, interactionSource = interactionSource, indication = null, role = Role.RadioButton, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .heightIn(min = DcTokens.DESKTOP.buttonHeight.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(DesktopIcons.Check, contentDescription = "Selected", tint = DesktopColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = DesktopType.rowTitle, color = DesktopColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = DesktopType.caption, color = DesktopColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A row with an on/off switch; a click anywhere on the row or Space flips it once. */
@Composable
fun DesktopSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val alpha = if (enabled) 1f else DcTokens.DISABLED_ALPHA
    Row(
        modifier = modifier
            .desktopFocusRing(focused, DcTokens.Radius.CARD.dp)
            .toggleable(checked, interactionSource, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .fillMaxWidth()
            .heightIn(min = DcTokens.DESKTOP.rowMinHeight.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = DesktopType.rowTitle, color = DesktopColors.TextPrimary.copy(alpha = alpha))
            if (supporting != null) {
                Text(supporting, style = DesktopType.caption, color = DesktopColors.TextSecondary.copy(alpha = alpha))
            }
        }
        Box(
            Modifier
                .size(width = 46.dp, height = 28.dp)
                .background(if (checked) DesktopColors.Accent else DesktopColors.SurfaceSelected, RoundedCornerShape(14.dp))
                .padding(4.dp)
        ) {
            Box(
                Modifier
                    .offset(x = if (checked) 18.dp else 0.dp)
                    .size(20.dp)
                    .background(if (checked) Color.White else Color(0xFFC8CBD0), CircleShape)
            )
        }
    }
}

/** A note in a page: icon and text that says what to do. */
@Composable
fun DesktopBanner(text: String, modifier: Modifier = Modifier, kind: DcBannerKind = DcBannerKind.Info) {
    val colors = desktopBannerColors(kind)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.container, RoundedCornerShape(DcTokens.Radius.CARD.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            if (kind == DcBannerKind.Info) DesktopIcons.Info else DesktopIcons.Warning,
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.size(20.dp)
        )
        Text(text, style = DesktopType.body, color = DesktopColors.TextPrimary, modifier = Modifier.weight(1f))
    }
}

/** A small pill with a dot: "Watching for new videos", "Cancelled", "Uploading 42 %". */
@Composable
fun DesktopStatusChip(text: String, modifier: Modifier = Modifier, kind: DcStatusKind = DcStatusKind.Neutral) {
    val colors = desktopStatusColors(kind)
    Row(
        modifier = modifier
            .background(colors.container, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (kind != DcStatusKind.Brand) {
            Box(Modifier.size(7.dp).background(colors.content, CircleShape))
        }
        Text(
            text,
            style = DesktopType.caption.copy(fontWeight = FontWeight.Medium),
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Task P.6: one entry of a side list (the settings shell, the Help Center's topics): lit on hover, filled when it is
 * the page shown, the keyboard focus ring when tabbed to.
 */
@Composable
fun DesktopSideItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val radius = DcTokens.Radius.CONTROL.dp
    val content = when {
        !enabled -> DesktopColors.TextTertiary
        isSelected -> DesktopColors.TextPrimary
        else -> DesktopColors.TextSecondary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .desktopFocusRing(focused, radius)
            .background(
                when {
                    isSelected -> DesktopColors.SurfaceSelected
                    hovered && enabled -> DesktopColors.SurfaceRaised
                    else -> Color.Transparent
                },
                RoundedCornerShape(radius)
            )
            .hoverable(interactionSource, enabled)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, style = DesktopType.body, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Task P.6 (decision D-P2): a question before something that can't be undone in one click (removing a folder, a
 * device, a PC, or write access). The confirm button is red text when [destructive].
 */
@Composable
fun DesktopConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DesktopColors.Surface,
        titleContentColor = DesktopColors.TextPrimary,
        textContentColor = DesktopColors.TextSecondary,
        shape = RoundedCornerShape(DcTokens.Radius.SHEET.dp),
        title = { Text(title, style = DesktopType.title) },
        text = { Text(text, style = DesktopType.body) },
        confirmButton = {
            DesktopButton(confirmLabel, onClick = onConfirm, kind = if (destructive) DcButtonKind.Destructive else DcButtonKind.Primary)
        },
        dismissButton = { DesktopButton("Cancel", onClick = onDismiss, kind = DcButtonKind.Secondary) }
    )
}

/** Task P.6: a section title with its actions on the right ("Paired devices" · Pair a device). */
@Composable
fun DesktopSectionHeader(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 28.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = DesktopType.title, color = DesktopColors.TextPrimary, modifier = Modifier.weight(1f))
        actions()
    }
}
