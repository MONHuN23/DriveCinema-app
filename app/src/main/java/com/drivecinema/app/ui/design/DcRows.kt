package com.drivecinema.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.icons.AppIcons

/**
 * A list row's colours (design §3.4). Rows inside a [DcGroup] keep a clear [container]; standalone TV rows pass
 * Surface 1. A focused row turns white; a destructive one keeps red text on it.
 */
fun dcRowColors(destructive: Boolean = false, container: Color = Color.Transparent): DcControlColors = DcControlColors(
    container = container,
    content = if (destructive) DcColors.Danger else DcColors.Text1,
    focusedContainer = DcColors.Focus,
    focusedContent = if (destructive) DcColors.Brand else DcColors.Ink
)

/** A group of rows on Surface 1 (phone settings, desktop-like lists). Separate rows with [DcGroupDivider]. */
@Composable
fun DcGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DcColors.Surface1, RoundedCornerShape(DcTokens.Radius.CARD.dp)),
        content = content
    )
}

/** The small title above a [DcGroup] ("LIBRARY"). */
@Composable
fun DcGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = DcTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
        color = DcColors.Text3,
        modifier = modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp)
    )
}

/** A 1 dp divider between rows of a [DcGroup], inset past the rows' icons. */
@Composable
fun DcGroupDivider(startInset: Dp = 54.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(1.dp)
            .background(DcColors.Divider)
    )
}

/**
 * A settings-style row: optional icon, title, optional supporting text, optional trailing content (a value, a
 * chevron, a badge). With [onClick] it is focusable and clickable; without, it only shows information.
 */
@Composable
fun DcListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    container: Color = Color.Transparent,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = dcRowColors(destructive, container)
    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        Modifier
            .indication(interactionSource, DcFocusIndication(colors, DcTokens.Radius.CONTROL.dp, DcTokens.Focus.ROW_SCALE))
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
    } else {
        Modifier.background(container, RoundedCornerShape(DcTokens.Radius.CONTROL.dp))
    }
    DcRowLayout(
        title = title,
        supporting = supporting,
        icon = icon,
        titleColor = colors.content,
        iconColor = if (destructive) DcColors.Danger else DcColors.Text2,
        modifier = modifier.then(clickModifier),
        trailing = trailing
    )
}

/**
 * A row with an on/off switch. The whole row toggles: a tap, or one OK press on the TV, flips it once
 * (`toggleable`, role Switch).
 */
@Composable
fun DcSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = Color.Transparent
) {
    val colors = dcRowColors(container = container)
    val interactionSource = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else DcTokens.DISABLED_ALPHA
    DcRowLayout(
        title = title,
        supporting = supporting,
        icon = icon,
        titleColor = colors.content.copy(alpha = alpha),
        iconColor = DcColors.Text2.copy(alpha = alpha),
        modifier = modifier
            .indication(interactionSource, DcFocusIndication(colors, DcTokens.Radius.CONTROL.dp, DcTokens.Focus.ROW_SCALE))
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        trailing = { DcSwitchTrack(checked) }
    )
}

/**
 * A row in a single-choice list (P.8: the player's audio and subtitle tracks). The chosen row has a check in front and
 * a Surface 3 fill; focus turns any row white (D-P1), so the chosen and the focused row never look alike. A disabled
 * row is dimmed and can't be focused. Role RadioButton, so the chosen state is announced.
 */
@Composable
fun DcChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true
) {
    val colors = dcRowColors(container = if (selected) DcColors.Surface3 else Color.Transparent)
    val interactionSource = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else DcTokens.DISABLED_ALPHA
    DcRowLayout(
        title = title,
        supporting = supporting,
        icon = null,
        titleColor = colors.content.copy(alpha = alpha),
        iconColor = DcColors.Text1,
        supportingColor = DcColors.Text2.copy(alpha = alpha),
        modifier = modifier
            .indication(interactionSource, DcFocusIndication(colors, DcTokens.Radius.CONTROL.dp, DcTokens.Focus.ROW_SCALE))
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        leading = {
            Box(Modifier.size(DcTheme.formFactor.iconSize), contentAlignment = Alignment.Center) {
                if (selected) Icon(AppIcons.Check, contentDescription = "Selected", tint = DcColors.Text1)
            }
        }
    )
}

/** The switch's look; the row carries its semantics. Brand red when on. */
@Composable
private fun DcSwitchTrack(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 28.dp)
            .background(if (checked) DcColors.Brand else DcColors.Surface3, RoundedCornerShape(14.dp))
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

@Composable
private fun DcRowLayout(
    title: String,
    supporting: String?,
    icon: ImageVector?,
    titleColor: Color,
    iconColor: Color,
    modifier: Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    supportingColor: Color = DcColors.Text2,
    leading: (@Composable () -> Unit)? = null
) {
    val form = DcTheme.formFactor
    val type = form.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = form.rowMinHeight)
            .padding(horizontal = if (form.isTv) 18.dp else 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (form.isTv) 16.dp else 14.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(form.iconSize))
        }
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = type.rowTitle, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = type.caption, color = supportingColor)
            }
        }
        if (trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = trailing
            )
        }
    }
}

/** The usual trailing content of a row that opens another screen: an optional value, then a chevron. */
@Composable
fun RowScope.DcChevron(value: String? = null) {
    if (value != null) {
        Text(value, style = DcTheme.type.caption, color = DcColors.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(AppIcons.ChevronRight, contentDescription = null, tint = DcColors.Text3, modifier = Modifier.size(20.dp))
}
