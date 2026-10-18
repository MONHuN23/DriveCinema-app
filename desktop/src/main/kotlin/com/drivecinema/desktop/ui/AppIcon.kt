package com.drivecinema.desktop.ui

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

private const val ICON_RESOURCE = "/drivecinema_icon.png"

/** Window/taskbar icon: the DriveCinema logo emblem, the same image as the Android launcher and the `.deb` icon. */
fun appIconPainter(): Painter {
    val bytes = requireNotNull(object {}.javaClass.getResourceAsStream(ICON_RESOURCE)) { "Missing $ICON_RESOURCE" }
        .use { it.readBytes() }
    return BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}
