package com.drivecinema.app.core.devicesync

import com.drivecinema.app.core.devicesync.DeviceSyncRunner.Trigger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Milestone Q: one sync at a time; returning to the foreground syncs at most once a minute (design D7). */
class DeviceSyncRunnerTest {

    private var now = 1_000_000L
    private val runner = DeviceSyncRunner(minIntervalMs = 60_000L) { now }
    private val ok = DeviceSyncResult.Synced(0, 0, false, 0)

    @Test
    fun foregroundRunsAreThrottledOtherTriggersAreNot() = runBlocking {
        var runs = 0
        val block: suspend () -> DeviceSyncResult = { runs++; ok }
        assertEquals(ok, runner.run(Trigger.STARTUP, block))
        now += 30_000
        assertNull(runner.run(Trigger.FOREGROUND, block))
        assertEquals(ok, runner.run(Trigger.PLAYER_CLOSED, block))
        now += 60_000
        assertEquals(ok, runner.run(Trigger.FOREGROUND, block))
        assertEquals(3, runs)
    }

    @Test
    fun aForcedRunWaitsForTheRunningOneAndAForegroundRunIsSkipped() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val first = async { runner.run(Trigger.STARTUP) { order += "start"; release.await(); order += "end"; ok } }
        while (order.isEmpty()) kotlinx.coroutines.yield()
        now += 120_000
        assertNull("busy", runner.run(Trigger.FOREGROUND) { order += "foreground"; ok })
        val second = async { runner.run(Trigger.PLAYER_CLOSED) { order += "player"; ok } }
        kotlinx.coroutines.yield()
        release.complete(Unit)
        first.await(); second.await()
        assertEquals(listOf("start", "end", "player"), order)
    }
}
