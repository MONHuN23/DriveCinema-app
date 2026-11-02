package com.drivecinema.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.drivecinema.app.ui.icons.MaterialIconPaths as P

/**
 * Task L.2: the Material Icons the desktop UI uses, as vectors. Emoji came from the system's emoji font, so they looked
 * different (or were missing) on Fedora, Ubuntu and Windows; these look the same everywhere. The path data is shared
 * with Android in [com.drivecinema.app.ui.icons.MaterialIconPaths].
 */
object DesktopIcons {
    val PlayArrow by icon("PlayArrow", P.PLAY_ARROW)
    val Pause by icon("Pause", P.PAUSE)
    /** Circular arrow for "back 10 s"; mirrored for "forward 10 s" (see [SeekIcon]). */
    val Replay by icon("Replay", P.REPLAY)
    val SkipNext by icon("SkipNext", P.SKIP_NEXT)
    val VolumeUp by icon("VolumeUp", P.VOLUME_UP)
    val VolumeOff by icon("VolumeOff", P.VOLUME_OFF)
    val Fullscreen by icon("Fullscreen", P.FULLSCREEN)
    val FullscreenExit by icon("FullscreenExit", P.FULLSCREEN_EXIT)
    val Subtitles by icon("Subtitles", P.SUBTITLES)
    val Audiotrack by icon("Audiotrack", P.AUDIOTRACK)
    val ArrowBack by icon("ArrowBack", P.ARROW_BACK)
    val ExpandMore by icon("ExpandMore", P.EXPAND_MORE)
    val ChevronRight by icon("ChevronRight", P.CHEVRON_RIGHT)
    val Search by icon("Search", P.SEARCH)
    val Refresh by icon("Refresh", P.REFRESH)
    val Add by icon("Add", P.ADD)
    val Check by icon("Check", P.CHECK)
    val CheckCircle by icon("CheckCircle", P.CHECK_CIRCLE)
    val Star by icon("Star", P.STAR)
    val Info by icon("Info", P.INFO)
    val Warning by icon("Warning", P.WARNING)
    val Folder by icon("Folder", P.FOLDER)
    val Storage by icon("Storage", P.STORAGE)
    val CloudUpload by icon("CloudUpload", P.CLOUD_UPLOAD)
    val WifiTethering by icon("WifiTethering", P.WIFI_TETHERING)
    val DesktopWindows by icon("DesktopWindows", P.DESKTOP_WINDOWS)
    val HelpOutline by icon("HelpOutline", P.HELP_OUTLINE)
    val Logout by icon("Logout", P.LOGOUT)
    val Close by icon("Close", P.CLOSE)
    val Person by icon("Person", P.PERSON)
    val Tv by icon("Tv", P.TV)
    val Home by icon("Home", P.HOME)
    val VideoLibrary by icon("VideoLibrary", P.VIDEO_LIBRARY)
    val Group by icon("Group", P.GROUP)
    // Milestone P
    val Settings by icon("Settings", P.SETTINGS)
    val SwapHoriz by icon("SwapHoriz", P.SWAP_HORIZ)
    val Smartphone by icon("Smartphone", P.SMARTPHONE)
    val Edit by icon("Edit", P.EDIT)
    val DeleteOutline by icon("DeleteOutline", P.DELETE_OUTLINE)
    val Lock by icon("Lock", P.LOCK)
    val Wifi by icon("Wifi", P.WIFI)
    val VpnKey by icon("VpnKey", P.VPN_KEY)
    val FastForward by icon("FastForward", P.FAST_FORWARD)
    val Movie by icon("Movie", P.MOVIE)
    val Article by icon("Article", P.ARTICLE)
    val ContentCopy by icon("ContentCopy", P.CONTENT_COPY)
    val InsertDriveFile by icon("InsertDriveFile", P.INSERT_DRIVE_FILE)
    /** Material "swap_vert", for the Films/Series sort menu; desktop-only, so its path lives here. */
    val Sort by icon("Sort", SWAP_VERT)

    /** Every icon, for the test that checks each path parses. */
    internal val all: List<ImageVector>
        get() = listOf(
            PlayArrow, Pause, Replay, SkipNext, VolumeUp, VolumeOff, Fullscreen, FullscreenExit, Subtitles, Audiotrack, ArrowBack, ExpandMore, ChevronRight, Search, Refresh, Add, Check, CheckCircle, Star, Info, Warning, Folder, Storage, CloudUpload, WifiTethering, DesktopWindows, HelpOutline, Logout, Close, Person, Tv, Home, VideoLibrary, Group,
            Settings, SwapHoriz, Smartphone, Edit, DeleteOutline, Lock, Wifi, VpnKey, FastForward, Movie, Article, ContentCopy, InsertDriveFile, Sort
        )
}

private const val SWAP_VERT = "M16 17.01V10h-2v7.01h-3L15 21l4-3.99h-3zM9 3L5 6.99h3V14h2V6.99h3L9 3z"

/** Built on first use; tinted by `Icon`, so the fill colour here doesn't matter. */
private fun icon(name: String, pathData: String): Lazy<ImageVector> = lazy {
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()
}
