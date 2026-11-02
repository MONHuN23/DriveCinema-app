package com.drivecinema.app.ui.naming

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drivecinema.app.core.metadata.TmdbMetadata
import com.drivecinema.app.core.naming.NamingTexts
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.TmdbImages
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcControlColors
import com.drivecinema.app.ui.design.DcFocusIndication
import com.drivecinema.app.ui.design.DcIconButton
import com.drivecinema.app.ui.design.DcStatusChip
import com.drivecinema.app.ui.design.DcStatusKind
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.icons.AppIcons

/** A focused naming row on the TV: white with dark text, like the Settings and episode rows. */
private val namingRowColors = DcControlColors(
    container = Color.Transparent,
    content = DcColors.Text1,
    focusedContainer = DcColors.Focus,
    focusedContent = DcColors.Ink
)

/** A focused naming helper row: a raised surface, the content in its own colours. */
private val raisedRowColors = DcControlColors(
    container = Color.Transparent,
    content = DcColors.Text1,
    focusedContainer = DcColors.Surface3,
    focusedContent = DcColors.Text1
)

/** How a naming row looks focused. */
internal enum class NamingRowStyle {
    /** White, the content drawn in Ink: the Naming check, like Settings. */
    FILLED,

    /**
     * The naming helper (UI task ui/tv-browse-naming): [DcColors.Surface3] and the white ring, the content left as it
     * is. The white fill draws everything in the row in Ink, posters too: a focused result's poster was a black box.
     */
    RAISED
}

internal fun namingRowIndication(style: NamingRowStyle): DcFocusIndication = when (style) {
    NamingRowStyle.FILLED -> DcFocusIndication(namingRowColors, DcTokens.Radius.CONTROL.dp, DcTokens.Focus.ROW_SCALE)
    NamingRowStyle.RAISED ->
        DcFocusIndication(raisedRowColors, DcTokens.Radius.CONTROL.dp, DcTokens.Focus.ROW_SCALE, recolorContent = false)
}

/**
 * One line of the naming screens. With [onClick] it is a button (a result, a season header). Without, it only shows
 * information: on the TV it can still take the focus (OK does nothing), so the remote can scroll through a season.
 */
@Composable
internal fun NamingRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    focusableOnTv: Boolean = true,
    style: NamingRowStyle = NamingRowStyle.FILLED,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isTv = DcTheme.formFactor.isTv
    val interactionSource = remember { MutableInteractionSource() }
    val indication = namingRowIndication(style)
    val interactive = when {
        onClick != null -> Modifier
            .indication(interactionSource, indication)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
        isTv && focusableOnTv -> Modifier.indication(interactionSource, indication).focusable(interactionSource = interactionSource)
        else -> Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isTv) 48.dp else 44.dp)
            .then(interactive)
            .padding(horizontal = if (isTv) 14.dp else 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = trailing)
        }
    }
}

/** A row's main line. */
@Composable
internal fun NamingLine(text: String, dimmed: Boolean = false) {
    Text(
        text,
        style = DcTheme.type.rowTitle,
        color = if (dimmed) DcColors.Text3 else DcColors.Text1,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/** A file or folder name, in a monospaced face so the exact spelling reads. */
@Composable
internal fun NamingName(text: String, dimmed: Boolean = false) {
    Text(
        text,
        style = DcTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
        color = if (dimmed) DcColors.Text3 else DcColors.Text2
    )
}

/** A note under a row: why a file is a problem, or a warning. */
@Composable
internal fun NamingNote(text: String, warning: Boolean = false) {
    Text(text, style = DcTheme.type.caption, color = if (warning) DcColors.Warning else DcColors.Text2)
}

/** The phone's copy button (the TV is view-only: none). */
@Composable
internal fun CopyButton(what: String, onCopy: () -> Unit) {
    DcIconButton(AppIcons.ContentCopy, contentDescription = "Copy $what", onClick = onCopy)
}

/** A season group's header: opens and closes the season. */
@Composable
internal fun SeasonHeader(
    title: String,
    supporting: String,
    isOpen: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    style: NamingRowStyle = NamingRowStyle.FILLED
) {
    NamingRow(
        modifier = modifier.padding(top = 6.dp),
        onClick = onToggle,
        style = style,
        trailing = {
            Icon(
                if (isOpen) AppIcons.ExpandMore else AppIcons.ChevronRight,
                contentDescription = if (isOpen) "Close" else "Open",
                tint = DcColors.Text2,
                modifier = Modifier.size(22.dp)
            )
        }
    ) {
        Text(title, style = DcTheme.type.title, color = DcColors.Text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(supporting, style = DcTheme.type.caption, color = DcColors.Text2)
    }
}

/** Small chips in a row: Series / Films, the layouts, the check's filter. The chosen one is filled. */
@Composable
internal fun <T> NamingChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "naming_chip"
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEach { option ->
            DcButton(
                label(option),
                onClick = { onSelect(option) },
                kind = if (option == selected) DcButtonKind.Primary else DcButtonKind.Secondary,
                small = true,
                icon = if (option == selected) AppIcons.Check else null,
                modifier = Modifier.testTag("${tagPrefix}_$option")
            )
        }
    }
}

/** A loading line in the flow of the list. */
@Composable
internal fun NamingLoading(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(color = DcColors.Text2, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Text(text, style = DcTheme.type.body, color = DcColors.Text2)
    }
}

/** A TMDB poster, or a plain tile without one; [width] wide, 2:3. */
@Composable
internal fun NamingPoster(posterPath: String?, width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(if (width > 60.dp) DcTokens.Radius.CONTROL.dp else DcTokens.Radius.TAG.dp)
    val size = Modifier.size(width = width, height = width * 1.5f)
    val poster = TmdbImages.poster(posterPath)
    if (poster != null) {
        AsyncImage(
            model = poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.then(size).clip(shape).background(DcColors.Surface2)
        )
    } else {
        Box(modifier.then(size).background(DcColors.Surface2, shape))
    }
}

/**
 * A TMDB result: poster, name and year, the Animation chip, the overview's first line. Its focus keeps the poster's
 * colours ([NamingRowStyle.RAISED]).
 */
@Composable
internal fun ResultRow(
    metadata: TmdbMetadata,
    animated: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NamingRow(
        modifier = modifier,
        onClick = onClick,
        style = NamingRowStyle.RAISED,
        leading = { NamingPoster(metadata.posterPath, 44.dp, Modifier.testTag("naming_poster_${metadata.tmdbId}")) }
    ) {
        NamingLine(metadata.title + (metadata.year?.let { " ($it)" } ?: ""))
        if (animated) DcStatusChip(NamingTexts.ANIMATION, kind = DcStatusKind.Brand)
        metadata.overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = DcTheme.type.caption, color = DcColors.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A status icon in front of a check row. */
@Composable
internal fun StatusIcon(icon: ImageVector, tint: Color, description: String) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
}
