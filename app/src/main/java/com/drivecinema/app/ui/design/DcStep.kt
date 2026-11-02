package com.drivecinema.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme

/**
 * Milestone P: one numbered step of a task done in order (pairing a PC, a Help Center topic). [isHeading]: the title
 * names a part of a form (My PCs' steps); a Help topic's step is a sentence to read, not a heading.
 */
@Composable
fun DcStep(
    number: Int,
    title: String,
    modifier: Modifier = Modifier,
    isHeading: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val tv = DcTheme.formFactor.isTv
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(if (tv) 32.dp else 28.dp).background(DcColors.Brand, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", style = DcTheme.type.label, color = DcColors.OnBrand)
        }
        Column(Modifier.weight(1f).padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = DcTheme.type.body,
                color = DcColors.Text1,
                modifier = if (isHeading) Modifier.semantics { heading() } else Modifier
            )
            content()
        }
    }
}
