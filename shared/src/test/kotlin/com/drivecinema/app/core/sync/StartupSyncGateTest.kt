package com.drivecinema.app.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Report F-04: every screen rotation re-read the whole Drive library (≈12 requests, 4–6 s). */
class StartupSyncGateTest {

    @Test
    fun coldStartAlwaysSyncs() {
        assertTrue(StartupSyncGate().shouldSync("root", isRecreated = false))
    }

    @Test
    fun rotationAfterAFinishedSyncSkipsIt() {
        val gate = StartupSyncGate()
        gate.markSynced("root")
        assertFalse(gate.shouldSync("root", isRecreated = true))
    }

    @Test
    fun rotationDuringAnUnfinishedSyncRunsItAgain() {
        // The old activity's sync is cancelled with it and never marked, so the new one must sync.
        assertTrue(StartupSyncGate().shouldSync("root", isRecreated = true))
    }

    @Test
    fun processRestoreSyncs() {
        // After process death the activity gets saved state, but the gate is new.
        val freshProcessGate = StartupSyncGate()
        assertTrue(freshProcessGate.shouldSync("root", isRecreated = true))
    }

    @Test
    fun rotationAfterSwitchingToAnotherFolderSyncsThatFolder() {
        val gate = StartupSyncGate()
        gate.markSynced("old-root")
        assertTrue(gate.shouldSync("new-root", isRecreated = true))
    }

    @Test
    fun newActivityWithoutSavedStateSyncsEvenIfAlreadySynced() {
        // Leaving with Back and reopening while the process lives keeps the previous behaviour: sync.
        val gate = StartupSyncGate()
        gate.markSynced("root")
        assertTrue(gate.shouldSync("root", isRecreated = false))
    }
}
