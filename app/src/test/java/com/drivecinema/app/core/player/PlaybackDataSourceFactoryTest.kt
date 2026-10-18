package com.drivecinema.app.core.player

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/** Task C.3: one data source plays local SAF documents and still streams Drive through the token stack. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlaybackDataSourceFactoryTest {

    private lateinit var context: Context
    private lateinit var videoFile: File
    private val videoBytes = ByteArray(64 * 1024) { (it % 251).toByte() }

    private val tokenProvider = object : TokenProvider {
        override fun getAccessToken(): String = "drive_token"
        override fun forceRefresh(): String = "drive_token"
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        videoFile = File(context.cacheDir, "local-video.mkv").apply { writeBytes(videoBytes) }
        TestVideoProvider.file = videoFile
        Robolectric.setupContentProvider(TestVideoProvider::class.java, AUTHORITY)
    }

    @After
    fun tearDown() {
        videoFile.delete()
    }

    private fun DataSource.readAll(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    @Test
    fun readsLocalContentUri() {
        val dataSource = PlaybackDataSourceFactory.create(context, tokenProvider).createDataSource()
        try {
            val length = dataSource.open(DataSpec(LOCAL_URI))
            assertEquals(videoBytes.size.toLong(), length)
            assertArrayEquals(videoBytes, dataSource.readAll())
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun seeksWithinLocalContentUri() {
        val dataSource = PlaybackDataSourceFactory.create(context, tokenProvider).createDataSource()
        try {
            val offset = 40_000L
            dataSource.open(DataSpec.Builder().setUri(LOCAL_URI).setPosition(offset).build())
            assertArrayEquals(videoBytes.copyOfRange(offset.toInt(), videoBytes.size), dataSource.readAll())
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun driveHttpStreamStillUsesTokenInterceptor() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("drive bytes"))
        server.start()
        val dataSource = PlaybackDataSourceFactory.create(context, tokenProvider).createDataSource()
        try {
            dataSource.open(DataSpec(Uri.parse(server.url("/drive/v3/files/abc?alt=media").toString())))
            assertEquals("drive bytes", String(dataSource.readAll()))
            val request = server.takeRequest()
            assertEquals("Bearer drive_token", request.getHeader("Authorization"))
            assertEquals("DriveCinema/1.0 (Android)", request.getHeader("User-Agent"))
        } finally {
            dataSource.close()
            server.shutdown()
        }
    }

    class TestVideoProvider : ContentProvider() {
        companion object {
            lateinit var file: File
        }

        override fun onCreate(): Boolean = true
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        override fun getType(uri: Uri): String = "video/x-matroska"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private companion object {
        const val AUTHORITY = "com.drivecinema.test.videos"
        val LOCAL_URI: Uri = Uri.parse("content://$AUTHORITY/movies/local-video.mkv")
    }
}
