package com.drivecinema.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme

/** A [DcButton]'s colours ([DcButtonKind] is shared with the desktop). Focused neutral buttons turn white; a focused primary keeps its red (white text 4.8:1). */
fun dcButtonColors(kind: DcButtonKind): DcControlColors = when (kind) {
    DcButtonKind.Primary -> DcControlColors(DcColors.Brand, DcColors.OnBrand, DcColors.Brand, DcColors.OnBrand)
    DcButtonKind.Secondary -> DcControlColors(DcColors.Surface2, DcColors.Text1, DcColors.Focus, DcColors.Ink)
    DcButtonKind.Tertiary -> DcControlColors(Color.Transparent, DcColors.Text2, DcColors.Focus, DcColors.Ink)
    DcButtonKind.Destructive -> DcControlColors(Color.Transparent, DcColors.Danger, DcColors.Focus, DcColors.Brand)
}

/** A disabled button's fill: filled kinds keep a quiet shape, text kinds stay bare. */
fun dcDisabledContainer(kind: DcButtonKind): Color = when (kind) {
    DcButtonKind.Primary, DcButtonKind.Secondary -> DcColors.Surface1
    DcButtonKind.Tertiary, DcButtonKind.Destructive -> Color.Transparent
}

/**
 * Milestone P's button for the phone and the TV. OK, Enter and a tap all go through `clickable`, so one press is one
 * click. While [loading] the button keeps its focus and width but ignores presses; a disabled button can't be
 * focused, so say why next to it.
 */
@Composable
fun DcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: DcButtonKind = DcButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    small: Boolean = false
) {
    val form = DcTheme.formFactor
    val colors = dcButtonColors(kind)
    val interactionSource = remember { MutableInteractionSource() }
    val radius = DcTokens.Radius.CONTROL.dp
    val content = if (enabled) colors.content else DcColors.Text1.copy(alpha = DcTokens.DISABLED_ALPHA)
    val textOnly = kind == DcButtonKind.Tertiary || kind == DcButtonKind.Destructive
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = if (small) form.smallButtonHeight else form.buttonHeight)
            .then(
                if (enabled) Modifier.indication(interactionSource, DcFocusIndication(colors, radius))
                else Modifier.background(dcDisabledContainer(kind), RoundedCornerShape(radius))
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = { if (!loading) onClick() }
            )
            .padding(horizontal = if (textOnly) 12.dp else if (small) 14.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Text(text = text, style = form.type.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A round icon-only button (Back, overflow). [onImage] gives it a dark glass fill so it reads over a backdrop. The
 * circle is 44 dp on the phone (48 on the TV); the touch target is 48 dp on both (Task P.5: the details pages' back
 * button test).
 */
@Composable
fun DcIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onImage: Boolean = false
) {
    val form = DcTheme.formFactor
    val interactionSource = remember { MutableInteractionSource() }
    val size = if (form.isTv) 48.dp else 44.dp
    val colors = DcControlColors(
        container = if (onImage) DcColors.Canvas.copy(alpha = 0.6f) else Color.Transparent,
        content = DcColors.Text1,
        focusedContainer = DcColors.Focus,
        focusedContent = DcColors.Ink
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(size).indication(interactionSource, DcFocusIndication(colors, size / 2)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, tint = colors.content, modifier = Modifier.size(24.dp))
        }
    }
}
