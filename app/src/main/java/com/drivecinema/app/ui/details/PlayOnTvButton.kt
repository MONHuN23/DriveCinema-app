package com.drivecinema.app.ui.details

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.icons.AppIcons

/** Milestone D: the phone's "Play on TV" under Play on the details screens (only shown with a linked TV). */
@Composable
fun PlayOnTvButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DcButton(
        label,
        onClick = onClick,
        kind = DcButtonKind.Secondary,
        icon = AppIcons.Tv,
        modifier = modifier.fillMaxWidth().testTag("play_on_tv")
    )
}
