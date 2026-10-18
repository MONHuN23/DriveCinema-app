package com.drivecinema.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** TMDB image URLs, same sizes as the Android app. */
object TmdbImages {
    fun poster(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w342$it" }
    fun backdrop(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w780$it" }
}

/**
 * Downloads images once: a small in-memory cache for scrolling and a disk cache in the app data directory,
 * so posters show instantly (and offline) on the next start.
 */
class ImageLoader(
    private val cacheDir: File,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val maxMemoryEntries: Int = 200
) {
    private val memory = object : LinkedHashMap<String, ByteArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>): Boolean = size > maxMemoryEntries
    }

    fun cacheFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(cacheDir, hash)
    }

    /** Encoded image bytes, or null when the image cannot be fetched (no network, 404). */
    suspend fun loadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        synchronized(memory) { memory[url] }?.let { return@withContext it }
        val file = cacheFile(url)
        val bytes = if (file.exists()) file.readBytes() else download(url)?.also { save(file, it) }
        bytes?.also { synchronized(memory) { memory[url] = it } }
    }

    suspend fun load(url: String): ImageBitmap? {
        val bytes = loadBytes(url) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
        }
    }

    private fun download(url: String): ByteArray? = try {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    } catch (_: Exception) {
        null
    }

    private fun save(file: File, bytes: ByteArray) {
        runCatching {
            cacheDir.mkdirs()
            val temp = File(cacheDir, "${file.name}.tmp")
            temp.writeBytes(bytes)
            temp.renameTo(file)
        }
    }
}

val LocalImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

/** Shows an image from [url]; the placeholder background stays when there is no URL or it fails to load. */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val loader = LocalImageLoader.current
    val bitmap by produceState<ImageBitmap?>(null, url, loader) {
        value = if (url != null && loader != null) loader.load(url) else null
    }
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription, modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(DesktopColors.SurfaceRaised))
    }
}
