package com.drivecinema.app.core.skipintro

/** What a remote key does to the "Skip intro" button on the TV (O.2); null = the player's usual handling. */
enum class SkipIntroKeyAction { SKIP, HIDE }

object SkipIntroKeys {
    /**
     * With the controls hidden and the button showing, OK skips the intro and Back hides the button for that intro
     * (without leaving the player). With the controls showing, the button is a normal focusable control, so the
     * usual handling applies (Back first hides the controls, as before).
     */
    fun action(isOk: Boolean, isBack: Boolean, controlsVisible: Boolean, buttonVisible: Boolean): SkipIntroKeyAction? =
        when {
            controlsVisible || !buttonVisible -> null
            isOk -> SkipIntroKeyAction.SKIP
            isBack -> SkipIntroKeyAction.HIDE
            else -> null
        }
}
