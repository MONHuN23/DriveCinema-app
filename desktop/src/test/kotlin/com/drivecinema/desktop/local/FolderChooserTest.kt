package com.drivecinema.desktop.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.swing.SwingUtilities

class FolderChooserTest {

    /**
     * Regression (Windows, 2026-10-19): "Add folder" showed "Module with the Main dispatcher is missing", because the
     * Swing dialog ran on `Dispatchers.Main`, which needs kotlinx-coroutines-swing. Like the app, the test classpath
     * has no Main dispatcher, so this fails if the chooser goes back to it.
     */
    @Test
    fun swingWorkRunsOnTheEventThreadWithoutAMainDispatcher() = runBlocking {
        // Precondition: like the packaged app, this classpath has no Main dispatcher (the old code failed here).
        assertTrue(runCatching { withContext(Dispatchers.Main) { } }.isFailure)
        val onEventThread = FolderChooser.onSwingThread { SwingUtilities.isEventDispatchThread() }
        assertTrue(onEventThread)
        // Also when called from a background thread, as the folder screen's scope may be.
        assertEquals(42, withContext(Dispatchers.IO) { FolderChooser.onSwingThread { 42 } })
    }

    @Test
    fun anErrorInTheDialogReachesTheCaller() = runBlocking {
        val error = runCatching { FolderChooser.onSwingThread { throw IllegalStateException("no display") } }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }
}
