package com.drivecinema.app.ui.setup

/** Milestone O wording, the same on phone, TV and desktop (design: `docs/design/o0-skip-intro.md` §9). */
object SkipIntroTexts {
    const val SWITCH_CAPTION =
        "Shows a Skip intro button when intro times are known. Sends the show, season, episode and video length " +
            "to SkipDB, and, if SkipDB has none, the show, season and episode to IntroDB."

    const val ATTRIBUTION =
        "Intro times from SkipDB (skipdb.tv), ODbL 1.0. Intro data provided by IntroDB (introdb.app)."
}
