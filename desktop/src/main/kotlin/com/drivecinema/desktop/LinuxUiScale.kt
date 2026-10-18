package com.drivecinema.desktop

import java.util.concurrent.TimeUnit

/**
 * Linux HiDPI: Java takes its UI scale from GDK_SCALE or GNOME's XSettings. On GNOME (Wayland, with Xwayland
 * native scaling) the XSettings scale is sometimes missing after login, so the window is drawn at 1x and
 * everything looks tiny. The X resource `Xft.dpi` (192 = 2x) is still right, so use it as a fallback.
 * Must run before anything touches AWT.
 */
object LinuxUiScale {
    private const val UI_SCALE_PROPERTY = "sun.java2d.uiScale"

    /** Whole scale factor from `xrdb -query` output, or null when it is 1x or unknown. Floor, so a text-only
     *  enlargement (Xft.dpi 144 at 1x) doesn't blow up the whole UI. */
    fun scaleFromXrdb(xrdbOutput: String): Int? {
        val dpi = Regex("""^Xft\.dpi:\s*(\d+(?:\.\d+)?)\s*$""", RegexOption.MULTILINE)
            .find(xrdbOutput)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val scale = (dpi / 96.0).toInt()
        return scale.takeIf { it >= 2 }
    }

    /** Sets [UI_SCALE_PROPERTY] when on Linux and neither the user nor the environment chose a scale.
     *  Returns the scale it set, or null. */
    fun apply(
        osName: String = System.getProperty("os.name").orEmpty(),
        env: (String) -> String? = System::getenv,
        readXrdb: () -> String? = ::queryXrdb,
        getProperty: (String) -> String? = System::getProperty,
        setProperty: (String, String) -> Unit = { k, v -> System.setProperty(k, v) }
    ): Int? {
        if (!osName.startsWith("Linux")) return null
        if (!env("GDK_SCALE").isNullOrBlank() || getProperty(UI_SCALE_PROPERTY) != null) return null
        val scale = readXrdb()?.let(::scaleFromXrdb) ?: return null
        setProperty(UI_SCALE_PROPERTY, scale.toString())
        return scale
    }

    private fun queryXrdb(): String? = runCatching {
        val process = ProcessBuilder("xrdb", "-query").redirectErrorStream(true).start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        process.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
