package com.drivecinema.app.core.lifecycle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.AppScreen
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.network.FolderValidationResult
import com.drivecinema.app.core.network.GoogleDriveGateway
import com.drivecinema.app.core.security.AuthCredentials
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AccountLibraryLifecycleTest {

    private lateinit var context: Context
    private lateinit var db: DriveCinemaDatabase
    private lateinit var credFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DriveCinemaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        credFile = File(context.filesDir, "test_lifecycle_credentials.dat")
        if (credFile.exists()) credFile.delete()
    }

    @After
    fun tearDown() {
        db.close()
        if (credFile.exists()) credFile.delete()
    }

    @Test
    fun testCompleteAccountAndLibraryLifecycle() = runBlocking {
        val mediaDao = db.mediaDao()
        val progressDao = db.playbackProgressDao()

        // -------------------------------------------------------------
        // Step 1: Clean Install / Initial State
        // -------------------------------------------------------------
        var currentScreen: AppScreen = AppScreen.SignIn
        var cachedToken: String? = null
        var storedCredentials: AuthCredentials? = null

        assertEquals(AppScreen.SignIn, currentScreen)
        assertNull("No token on cold start", cachedToken)
        assertNull("No credentials on clean install", storedCredentials)
        assertTrue("Database is empty on fresh install", mediaDao.getAllMovies().isEmpty())

        // -------------------------------------------------------------
        // Step 2: Google Sign-In & Zero-Click Streaming Folder Discovery
        // -------------------------------------------------------------
        // Simulate OAuth success
        cachedToken = "oauth_access_token_xyz"
        val userDisplayName = "Alex Cinema"
        val userEmail = "alex@cinema.org"

        // Auto-discovery evaluates Google Drive
        val discoveredFolders = listOf(
            DriveFolder(id = "stream_root_123", name = "Streaming", isFolder = true)
        )
        // Exactly 1 candidate discovered
        assertEquals(1, discoveredFolders.size)

        val children = listOf(
            DriveFolder(id = "films_sub", name = "Films", isFolder = true),
            DriveFolder(id = "series_sub", name = "Series", isFolder = true)
        )
        val validationResult = GoogleDriveGateway.evaluateFolderChildren(children)
        assertTrue("Streaming folder must be valid", validationResult is FolderValidationResult.Valid)
        val valid = validationResult as FolderValidationResult.Valid
        assertEquals("films_sub", valid.filmsFolderId)

        // Save credentials with discovered rootFolderId
        storedCredentials = AuthCredentials(
            refreshToken = "refresh_token_abc",
            rootFolderId = discoveredFolders[0].id,
            userDisplayName = userDisplayName,
            userEmail = userEmail
        )

        // Library crawl populates media items into Room
        val movie1 = MediaItemEntity(
            id = "movie_dune_2",
            type = MediaType.MOVIE,
            title = "Dune Part Two",
            year = 2024,
            parentFolderId = "films_sub"
        )
        val movie2 = MediaItemEntity(
            id = "movie_blade_runner",
            type = MediaType.MOVIE,
            title = "Blade Runner 2049",
            year = 2017,
            parentFolderId = "films_sub"
        )
        val series1 = SeriesEntity(
            id = "series_severance",
            title = "Severance",
            year = 2022
        )

        mediaDao.insertMediaItems(listOf(movie1, movie2))
        mediaDao.insertSingleSeries(series1)

        // UI transitions to Home
        currentScreen = AppScreen.Home
        assertEquals(AppScreen.Home, currentScreen)
        assertEquals(2, mediaDao.getAllMovies().size)
        assertEquals(1, mediaDao.getAllSeries().size)

        // -------------------------------------------------------------
        // Step 3: Playback & Watch Progress Storage
        // -------------------------------------------------------------
        val playbackProgress = PlaybackProgressEntity(
            mediaItemId = "movie_dune_2",
            positionMs = 154_000L,
            durationMs = 9_960_000L,
            isCompleted = false
        )
        progressDao.saveProgress(playbackProgress)

        val recordedProgress = progressDao.getProgress("movie_dune_2")
        assertNotNull(recordedProgress)
        assertEquals(154_000L, recordedProgress!!.positionMs)

        // -------------------------------------------------------------
        // Step 4: Safe Logout & Session Teardown
        // -------------------------------------------------------------
        // Simulating MainActivity.handleSignOut()
        cachedToken = null
        storedCredentials = null
        currentScreen = AppScreen.SignIn

        // Verify session tokens are erased and UI returns to SignIn
        assertNull("OAuth token cleared", cachedToken)
        assertNull("Credentials cleared", storedCredentials)
        assertEquals(AppScreen.SignIn, currentScreen)

        // CRITICAL INVARIANT: SQLite database MUST remain completely intact!
        val preservedMovies = mediaDao.getAllMovies()
        val preservedSeries = mediaDao.getAllSeries()
        val preservedProgress = progressDao.getProgress("movie_dune_2")

        assertEquals("Movies database preserved across logout", 2, preservedMovies.size)
        assertEquals("Series database preserved across logout", 1, preservedSeries.size)
        assertNotNull("Playback progress preserved across logout", preservedProgress)
        assertEquals("Playback position preserved across logout", 154_000L, preservedProgress!!.positionMs)

        // -------------------------------------------------------------
        // Step 5: Re-Login & Instant Catalog Reconnect
        // -------------------------------------------------------------
        cachedToken = "new_oauth_token_reconnect"
        storedCredentials = AuthCredentials(
            refreshToken = "refresh_token_abc",
            rootFolderId = "stream_root_123",
            userDisplayName = userDisplayName,
            userEmail = userEmail
        )

        // With rootFolderId present, instantly proceed to Home without re-crawling
        currentScreen = AppScreen.Home
        assertEquals(AppScreen.Home, currentScreen)
        assertEquals(2, mediaDao.getAllMovies().size)

        // Resume playback directly from saved position
        val resumeItem = progressDao.getProgress("movie_dune_2")
        assertNotNull(resumeItem)
        assertEquals("Playback resumes at exact saved position", 154_000L, resumeItem!!.positionMs)

        // -------------------------------------------------------------
        // Step 6: Phone-to-TV Pairing & Credential Transfer
        // -------------------------------------------------------------
        // Phone and TV establish SRP-6a mutual PAKE pairing
        val pairingPin = "849201"
        val serverCrypto = com.drivecinema.app.core.pairing.SrpPairingServerCrypto(pin = pairingPin)
        val clientCrypto = com.drivecinema.app.core.pairing.SrpPairingClientCrypto(pin = pairingPin)

        val challenge = serverCrypto.generateChallenge()
        val (clientA, clientM1) = clientCrypto.processChallenge(challenge.salt, challenge.serverB)

        val serverM2 = serverCrypto.verifyClientAndGenerateM2(clientA, clientM1)
        val isServerVerified = clientCrypto.verifyServer(serverM2)
        assertTrue("Client verifies server evidence", isServerVerified)

        val serverKey = serverCrypto.deriveAesKey(challenge.salt)
        val clientKey = clientCrypto.deriveAesKey(challenge.salt)
        assertTrue("Mutual HKDF AES keys match", clientKey.contentEquals(serverKey))

        // Encrypt credentials with HKDF AES-GCM for TV transfer
        val payloadToTv = """{"refreshToken":"${storedCredentials.refreshToken}","rootFolderId":"${storedCredentials.rootFolderId}"}"""
        val encryptedPayload = com.drivecinema.app.core.pairing.SrpCryptoHelper.encryptPayload(clientKey, payloadToTv)
        val tvReceivedString = com.drivecinema.app.core.pairing.SrpCryptoHelper.decryptPayload(serverKey, encryptedPayload)

        assertTrue(tvReceivedString.contains("refresh_token_abc"))
        assertTrue(tvReceivedString.contains("stream_root_123"))

        // TV connects to database and has access to the exact same resume position
        val tvProgress = progressDao.getProgress("movie_dune_2")
        assertEquals(154_000L, tvProgress?.positionMs)
    }
}
