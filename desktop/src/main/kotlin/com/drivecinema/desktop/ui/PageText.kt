package com.drivecinema.desktop.ui

import com.drivecinema.app.core.database.entity.ImportState
import com.drivecinema.app.ui.design.DcStatusKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Task P.6: the words the desktop's settings pages show, kept out of the composables so they can be tested (the
// desktop has no Compose UI-test library).

/**
 * [text] in at most [maxChars] characters, cut in the middle: a path keeps its drive and its last folders, a file name
 * its start and its extension ("C:\Users\Janix\Vid…\Breaking Bad\Season 01"). The end gets the larger share.
 */
fun middleEllipsis(text: String, maxChars: Int): String {
    require(maxChars >= 5) { "Too short to shorten: $maxChars" }
    if (text.length <= maxChars) return text
    val keep = maxChars - 1
    val head = keep * 2 / 5
    return text.take(head) + "…" + text.takeLast(keep - head)
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/** "today", "yesterday", "21 Oct", or "21 Oct 2025" in another year (design §5: "Paired 21 Oct"). */
fun friendlyDay(epochMillis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when {
        day == today -> "today"
        day == today.minusDays(1) -> "yesterday"
        day.year == today.year -> day.format(dayMonth)
        else -> day.format(dayMonthYear)
    }
}

/** "today 12:49", "yesterday 09:03", "27 Oct, 12:49" (design §5: "last used 27 Oct, 12:49"). */
fun friendlyDateTime(epochMillis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val day = friendlyDay(epochMillis, now, zone)
    val clock = Instant.ofEpochMilli(epochMillis).atZone(zone).format(time)
    return if (day == "today" || day == "yesterday") "$day $clock" else "$day, $clock"
}

/** "Paired 21 Oct · last used today 12:49" for a device paired with this computer. */
fun pairedDeviceLine(pairedAt: Long, lastSeenAt: Long?, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String =
    "Paired ${friendlyDay(pairedAt, now, zone)}" + (lastSeenAt?.let { " · last used ${friendlyDateTime(it, now, zone)}" } ?: "")

/** What a paired device is, for its icon. */
enum class DeviceKind { PHONE, TV, PC }

private val tvWords = setOf("tv", "bravia", "shield", "chromecast", "aftmm", "aftka", "aftr")
/** "DriveCinema desktop" is what a computer sends when it can't read its host name. */
private val pcWords = setOf("desktop", "laptop")
private val tvMakers = setOf("tcl", "hisense", "philips", "sony", "sharp", "toshiba", "thomson", "skyworth", "panasonic")

/**
 * A guess from the name a device paired with (pairing sends no type). Phones and TVs send "Maker Model" ("Google
 * Pixel 9a"), a computer its host name, which has no space ("DESKTOP-ELT3101", "janix-ubuntu"). A TV says so in its
 * name ("NVIDIA SHIELD Android TV") or is a TV maker's model that starts with the screen size ("TCL 55C6K").
 */
fun deviceKind(name: String): DeviceKind {
    val words = name.trim().lowercase(Locale.ROOT).split(' ', '-', '_').filter { it.isNotEmpty() }
    return when {
        !name.trim().contains(' ') || words.any { it in pcWords } -> DeviceKind.PC
        words.any { it in tvWords || it.endsWith("tv") } -> DeviceKind.TV
        words.first() in tvMakers && words.getOrNull(1)?.matches(Regex("\\d{2}[a-z].*")) == true -> DeviceKind.TV
        else -> DeviceKind.PHONE
    }
}

/** An import queue row's state chip (design §5). */
fun importChip(state: ImportState): Pair<String, DcStatusKind> = when (state) {
    ImportState.QUEUED -> "Waiting" to DcStatusKind.Neutral
    ImportState.UPLOADING -> "Uploading" to DcStatusKind.Brand
    ImportState.DONE -> "Done" to DcStatusKind.Success
    ImportState.NEEDS_REVIEW -> "Needs review" to DcStatusKind.Warning
    ImportState.FAILED -> "Failed" to DcStatusKind.Warning
    ImportState.CANCELLED -> "Cancelled" to DcStatusKind.Neutral
    ImportState.SKIPPED -> "Not uploaded" to DcStatusKind.Neutral
}

/** The one thing a queue row offers, as before: stop it, try it again, or upload a file that was already there. */
enum class ImportRowAction(val label: String) { CANCEL("Cancel"), RETRY("Retry"), UPLOAD("Upload") }

fun importRowAction(state: ImportState): ImportRowAction? = when (state) {
    ImportState.QUEUED, ImportState.UPLOADING -> ImportRowAction.CANCEL
    ImportState.FAILED, ImportState.CANCELLED, ImportState.NEEDS_REVIEW -> ImportRowAction.RETRY
    ImportState.SKIPPED -> ImportRowAction.UPLOAD
    ImportState.DONE -> null
}

/** The import watcher's chip: what it is doing now. */
fun watcherChip(isRunning: Boolean, pausedByUser: Boolean, stoppedReason: String?): Pair<String, DcStatusKind> = when {
    stoppedReason != null -> "Stopped" to DcStatusKind.Warning
    pausedByUser -> "Paused" to DcStatusKind.Neutral
    isRunning -> "Watching" to DcStatusKind.Success
    else -> "Not running" to DcStatusKind.Neutral
}

/** The last folder of [path] ("Season 01" of "D:\Series\Show\Season 01"), the folder row's title. */
fun folderName(path: String): String = path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifEmpty { path }
