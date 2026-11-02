package com.drivecinema.app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.drivecinema.app.ui.design.DcTokens

/**
 * Material's text styles, left at Material's defaults on purpose (Milestone P): Home's text that relies on them keeps
 * its look. New UI reads [DcType] through `DcTheme.type`.
 */
val Typography =
  Typography(
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      )
  )

/** Milestone P: the type roles of the design (docs/design/p0-ui-refresh.md §3.2), for the phone or the TV. */
@Immutable
data class DcType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val rowTitle: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle
) {
    companion object {
        val Phone = from(DcTokens.PHONE.type)
        val Tv = from(DcTokens.TV.type)

        fun from(scale: DcTokens.TypeScale): DcType = DcType(
            display = scale.display.toTextStyle(),
            headline = scale.headline.toTextStyle(),
            title = scale.title.toTextStyle(),
            rowTitle = scale.rowTitle.toTextStyle(),
            body = scale.body.toTextStyle(),
            label = scale.label.toTextStyle(),
            caption = scale.caption.toTextStyle()
        )
    }
}

private fun DcTokens.TextStyleSpec.toTextStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight(weight),
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp
)
