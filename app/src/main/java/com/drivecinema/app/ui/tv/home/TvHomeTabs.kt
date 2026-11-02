package com.drivecinema.app.ui.tv.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.ui.design.DcControlColors
import com.drivecinema.app.ui.design.DcFocusIndication
import com.drivecinema.app.ui.design.DcTokens

/**
 * TV Home's tabs (UI task ui/tv-browse-naming): Home, Films, Series, Search. Films and Series open the A–Z grids and
 * Search opens search, as the old "See all A–Z" headers and Search button did; Home is where you are. Focused, a tab
 * is the white pill of Milestone P's controls; the current one has a red bar under it.
 */
@Composable
fun TvHomeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = DcControlColors(
        container = Color.Transparent,
        content = if (selected) DcColors.Text1 else DcColors.Text2,
        focusedContainer = DcColors.Focus,
        focusedContent = DcColors.Ink
    )
    Column(
        modifier = modifier
            .indication(interactionSource, DcFocusIndication(colors, DcTokens.Radius.CONTROL.dp))
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
            Text(label, color = colors.content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        // The current tab's bar; the others keep the room, so the labels line up.
        Box(
            Modifier
                .width(24.dp)
                .height(3.dp)
                .background(if (selected) DcColors.Brand else Color.Transparent, RoundedCornerShape(2.dp))
        )
    }
}

/**
 * TV Home's rows scroll so the focused card's row sits [pivotPx] below the list's top, under the spotlight: the rows
 * above it leave the screen and the next one peeks in below (UI task ui/tv-browse-naming). The rows themselves keep
 * the default horizontal scrolling.
 */
@OptIn(ExperimentalFoundationApi::class)
class TvRowPivotSpec(private val pivotPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - pivotPx
}
