package com.drivecinema.app.ui.setup

/**
 * Milestone P (Task P.2): the names of the phone/TV Settings screen and its rows, in sentence case. The screens and the
 * in-app guide both use these, so the guide's "Settings → …" paths always name what the screen shows.
 */
object SettingsLabels {
    const val SETTINGS = "Settings"

    // Categories (the TV's left pane; the phone's group titles)
    const val LIBRARY = "Library"
    const val SOURCES = "Sources"
    const val PLAYBACK = "Playback"
    const val HELP_AND_ABOUT = "Help & about"
    const val ACCOUNT = "Account"

    const val REFRESH_LIBRARY = "Refresh library"
    const val SWITCH_LIBRARY_FOLDER = "Switch library folder"
    const val REVIEW_DUPLICATES = "Review possible duplicates"
    /** Milestone R: the naming helper (Settings) and its folder check (a series page). */
    const val NAMING_HELPER = "Naming helper"
    const val NAMING_CHECK = "Naming check"
    const val LOCAL_FOLDERS = "Local folders"
    const val ADD_FOLDER = "Add folder"
    const val MY_PCS = "My PCs"
    const val ADD_A_PC = "Add a PC"
    const val SEARCH_THIS_NETWORK = "Search this network"
    const val PAIR = "Pair"
    const val AWAY_FROM_HOME = "Away from home (optional)"
    const val TAILSCALE_ADDRESS = "Tailscale address"
    const val SET_TAILSCALE_ADDRESS = "Set Tailscale address"
    const val CHECK_NOW = "Check now"
    const val PAIR_A_TV = "Pair a TV"
    const val SKIP_INTRO = "Skip intro"
    /** Milestone Q. */
    const val SYNC_WITH_MY_DEVICES = "Sync with my devices"
    /** Milestone D: the phone's Play on TV links, and the TV's. */
    const val MY_TVS = "My TVs"
    const val LINKED_PHONES = "Linked phones"
    const val HELP_CENTER = "Help Center"
    const val ABOUT = "About & credits"
    const val SIGN_OUT = "Sign out"

    /** "Settings → Local folders → Add folder": a place in the phone/TV app, as the guide writes it. */
    fun path(vararg steps: String): String = (listOf(SETTINGS) + steps).joinToString(" → ")
}
