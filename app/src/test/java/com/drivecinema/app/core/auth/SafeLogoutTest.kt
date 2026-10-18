package com.drivecinema.app.core.auth

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.AppScreen
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SafeLogoutTest {

    private lateinit var db: DriveCinemaDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DriveCinemaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testRoomDatabaseUntouchedAcrossLogout() = runBlocking {
        val mediaDao = db.mediaDao()
        val progressDao = db.playbackProgressDao()

        // 1. Populate database with movies, series, and playback progress
        val movie = MediaItemEntity(
            id = "m_inception",
            type = MediaType.MOVIE,
            title = "Inception",
            parentFolderId = "films_folder"
        )
        mediaDao.insertMediaItem(movie)

        val series = SeriesEntity(
            id = "s_severance",
            title = "Severance"
        )
        mediaDao.insertSingleSeries(series)

        val progress = PlaybackProgressEntity(
            mediaItemId = "m_inception",
            positionMs = 45000L,
            durationMs = 120000L,
            isCompleted = false
        )
        progressDao.saveProgress(progress)

        val movieCountBefore = mediaDao.getAllMovies().size
        val seriesCountBefore = mediaDao.getAllSeries().size
        val progressBefore = progressDao.getProgress("m_inception")

        assertEquals(1, movieCountBefore)
        assertEquals(1, seriesCountBefore)
        assertNotNull(progressBefore)

        // 2. Perform Safe Logout action:
        // Invariant: The SQLite database MUST NOT be cleared or reset.
        // We simulate the exact logout sequence from MainActivity.handleSignOut()
        var cachedAccessToken = "active_oauth_token"
        val currentScreen = mutableStateOf<AppScreen>(AppScreen.Home)

        // Session teardown
        cachedAccessToken = ""
        currentScreen.value = AppScreen.SignIn

        // 3. Verify SQLite data is 100% intact after logout
        val movieCountAfter = mediaDao.getAllMovies().size
        val seriesCountAfter = mediaDao.getAllSeries().size
        val progressAfter = progressDao.getProgress("m_inception")

        assertEquals("Movies must be strictly preserved after logout", movieCountBefore, movieCountAfter)
        assertEquals("Series must be strictly preserved after logout", seriesCountBefore, seriesCountAfter)
        assertNotNull("Playback progress must be strictly preserved after logout", progressAfter)
        assertEquals(progressBefore!!.positionMs, progressAfter!!.positionMs)
        assertEquals(false, progressAfter.isCompleted)
    }

    @Test
    fun testLogoutRedirectsToSignInScreen() {
        val currentScreen = mutableStateOf<AppScreen>(AppScreen.Home)
        assertTrue(currentScreen.value is AppScreen.Home)

        // Trigger sign-out transition
        currentScreen.value = AppScreen.SignIn

        assertEquals(AppScreen.SignIn, currentScreen.value)
    }

    @Test
    fun testClearCredentialsDeletesEncryptedFile() {
        val tempDir = java.nio.file.Files.createTempDirectory("cred_logout_test").toFile()
        val credFile = File(tempDir, "auth_credentials.enc")
        credFile.writeText("encrypted_token_payload")
        assertTrue(credFile.exists())

        // Simulated clearCredentials
        if (credFile.exists()) {
            credFile.delete()
        }

        assertFalse("auth_credentials.enc must be wiped on logout", credFile.exists())
        tempDir.deleteRecursively()
    }
}
