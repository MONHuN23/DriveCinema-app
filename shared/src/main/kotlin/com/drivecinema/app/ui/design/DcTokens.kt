package com.drivecinema.app.ui.design

/**
 * Milestone P: the one source of DriveCinema's colours, corner radii, spacing, control sizes, focus values and type
 * scales (design: docs/design/p0-ui-refresh.md §3). Plain Kotlin, so `:shared` needs no Compose: the phone/TV app
 * (`theme/`) and the desktop (`DesktopTheme`) each turn these into their own Compose values.
 */
object DcTokens {

    /** ARGB colours. The app is dark-only. */
    object Colors {
        /** Screen background (Home's, unchanged). */
        const val CANVAS: Long = 0xFF0C0D10
        /** Cards, list groups, side panes. */
        const val SURFACE_1: Long = 0xFF16181D
        /** Secondary buttons, hover. */
        const val SURFACE_2: Long = 0xFF1E2128
        /** Selected item, pressed. */
        const val SURFACE_3: Long = 0xFF2A2D35
        /** 1 dp inset dividers inside a group. */
        const val DIVIDER: Long = 0xFF262A33
        /** Text-field borders. */
        const val OUTLINE: Long = 0xFF3A3F4A

        const val TEXT_1: Long = 0xFFF0F2F5
        const val TEXT_2: Long = 0xFFB0B3B8
        const val TEXT_3: Long = 0xFF9AA0A6

        /** The main action, selection and progress. Fills and indicators only, never text (4.1:1 on the canvas). */
        const val BRAND: Long = 0xFFE50914
        const val ON_BRAND: Long = 0xFFFFFFFF
        /** Brand at 18 %: a selected tab's or nav item's background. */
        const val BRAND_CONTAINER: Long = 0x2EE50914

        /** TV focus ring; focused rows and neutral buttons fill with it and draw their content in [INK]. */
        const val FOCUS: Long = 0xFFFFFFFF
        const val INK: Long = 0xFF0C0D10

        const val SUCCESS: Long = 0xFF81C995
        const val SUCCESS_DOT: Long = 0xFF34A853
        const val WARNING: Long = 0xFFF5B544
        /** Warning at 10 %: a warning banner's background. */
        const val WARNING_CONTAINER: Long = 0x1AF5B544
        /** Destructive actions (Remove, Sign out), as text. */
        const val DANGER: Long = 0xFFFF6B6B
        /** Danger at 12 %: an error banner's background. */
        const val DANGER_CONTAINER: Long = 0x1FFF6B6B
        const val RATING_STAR: Long = 0xFFF5C518

        /** Home's poster-card focus ring, kept as it is (decision D-P1). New controls focus in [FOCUS]. */
        const val CARD_FOCUS_BLUE: Long = 0xFF4D88FF
    }

    /** Corner radii in dp. Pills (fully round) only for chips and status. */
    object Radius {
        const val TAG = 6
        const val CONTROL = 10
        const val CARD = 14
        const val SHEET = 20
    }

    /** Spacing steps in dp. */
    object Space {
        const val XXS = 4
        const val XS = 8
        const val S = 12
        const val M = 16
        const val L = 24
        const val XL = 32
        const val XXL = 48
    }

    /** How focus looks on the TV (and with a keyboard on the phone). */
    object Focus {
        const val CONTROL_SCALE = 1.04f
        const val ROW_SCALE = 1.02f
        const val RING_WIDTH = 2
        /** Space between a control's edge and its ring. */
        const val RING_GAP = 2
        const val ANIMATION_MS = 150
    }

    /** Content of a disabled control. */
    const val DISABLED_ALPHA = 0.38f
    /** White overlay while a control is pressed. */
    const val PRESSED_OVERLAY_ALPHA = 0.10f

    /** One text style: size and line height in sp, weight 100–900. */
    data class TextStyleSpec(val sizeSp: Int, val lineHeightSp: Int, val weight: Int)

    data class TypeScale(
        /** Film or series title. */
        val display: TextStyleSpec,
        /** Screen titles. */
        val headline: TextStyleSpec,
        /** Sections, topic titles. */
        val title: TextStyleSpec,
        /** List rows, cards. */
        val rowTitle: TextStyleSpec,
        /** Descriptions, guide text. */
        val body: TextStyleSpec,
        /** Buttons, tabs. */
        val label: TextStyleSpec,
        /** Metadata, supporting text. */
        val caption: TextStyleSpec
    )

    /** Everything that differs between the phone, the TV (10-foot UI at 960×540 dp) and the desktop. */
    data class FormFactor(
        val type: TypeScale,
        val buttonHeight: Int,
        val smallButtonHeight: Int,
        val rowMinHeight: Int,
        val iconSize: Int,
        /** Side margin of a screen's content. */
        val sideMargin: Int
    )

    val PHONE = FormFactor(
        type = TypeScale(
            display = TextStyleSpec(28, 34, 700),
            headline = TextStyleSpec(22, 28, 700),
            title = TextStyleSpec(18, 24, 600),
            rowTitle = TextStyleSpec(16, 22, 500),
            body = TextStyleSpec(14, 20, 400),
            label = TextStyleSpec(14, 20, 600),
            caption = TextStyleSpec(12, 16, 400)
        ),
        buttonHeight = 48,
        smallButtonHeight = 36,
        rowMinHeight = 56,
        iconSize = 24,
        sideMargin = 16
    )

    val TV = FormFactor(
        type = TypeScale(
            display = TextStyleSpec(40, 48, 700),
            headline = TextStyleSpec(28, 34, 700),
            title = TextStyleSpec(22, 28, 600),
            rowTitle = TextStyleSpec(18, 24, 500),
            body = TextStyleSpec(16, 24, 400),
            label = TextStyleSpec(16, 22, 600),
            caption = TextStyleSpec(14, 20, 400)
        ),
        buttonHeight = 48,
        smallButtonHeight = 40,
        rowMinHeight = 64,
        iconSize = 28,
        sideMargin = 48
    )

    val DESKTOP = FormFactor(
        type = TypeScale(
            display = TextStyleSpec(32, 40, 700),
            headline = TextStyleSpec(26, 32, 700),
            title = TextStyleSpec(18, 24, 600),
            rowTitle = TextStyleSpec(15, 20, 500),
            body = TextStyleSpec(14, 21, 400),
            label = TextStyleSpec(14, 20, 600),
            caption = TextStyleSpec(12, 16, 400)
        ),
        buttonHeight = 40,
        smallButtonHeight = 32,
        rowMinHeight = 56,
        iconSize = 20,
        sideMargin = 40
    )
}
