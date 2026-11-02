package com.drivecinema.app.ui.navigation

import androidx.compose.runtime.snapshots.Snapshot
import com.drivecinema.app.core.logging.AppLog
import androidx.navigation3.runtime.NavBackStack
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.builtins.ListSerializer

/**
 * Task M.3 (F-38): the phone and TV back stack. Back pops to the screen you came from (not always Home), and
 * [attach] saves the stack with the activity's state, so a rotation keeps it. The activity owns it because sign-in,
 * pairing and library switching move between screens from outside composition.
 */
class AppNavigator(start: List<Screen>) {

    init {
        require(start.isNotEmpty()) { "The back stack needs a root screen" }
    }

    val backStack: NavBackStack<Screen> = NavBackStack(*start.toTypedArray())

    val current: Screen get() = backStack.last()

    /** Shows [screen] on top; opening the screen already shown does nothing. */
    fun open(screen: Screen) {
        if (backStack.last() != screen) backStack.add(screen)
        log("open")
    }

    /**
     * Shows [screen] in place of the top one (Task P.4: a Help topic's "Next" link), so Back still returns to where
     * the reader came from; at the root it opens [screen] on top.
     */
    fun replace(screen: Screen) {
        if (backStack.last() == screen) return
        if (backStack.size <= 1) return open(screen)
        Snapshot.withMutableSnapshot { backStack[backStack.lastIndex] = screen }
        log("replace")
    }

    /** Pops the top screen; false at the root, where Back leaves the app. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        log("back")
        return true
    }

    /**
     * Pops the top screen, or shows only [root] when nothing is under it. Cancelling a library switch (Task P.2): the
     * folder picker closes and Settings, which opened it, is shown again.
     */
    fun backOrResetTo(root: Screen) {
        if (!back()) resetTo(root)
    }

    /** Replaces the whole stack with [root] (signed in or out, library chosen or switched). */
    fun resetTo(root: Screen) {
        if (backStack.size == 1 && backStack.first() == root) return
        // One snapshot: NavDisplay never sees the empty stack in between.
        Snapshot.withMutableSnapshot {
            backStack.clear()
            backStack.add(root)
        }
        log("reset")
    }

    /** One Logcat line per change ("Nav" tag): the new stack and the app code that asked for it. */
    private fun log(action: String) {
        AppLog.d(TAG, "$action → ${backStack.toList()} (from ${caller()})")
    }

    companion object {
        const val TAG = "Nav"
        private const val STATE_KEY = "com.drivecinema.app.backStack"
        private val serializer = ListSerializer(Screen.serializer())

        /**
         * The navigator for an activity whose stored sign-in state starts at [root]: the stack saved before a rotation
         * if there is one, else just [root]. The stack is saved again whenever the activity saves its state.
         */
        fun attach(registry: SavedStateRegistry, root: Screen): AppNavigator {
            val saved = registry.consumeRestoredStateForKey(STATE_KEY)?.let { state ->
                runCatching { decodeFromSavedState(serializer, state) }
                    .onFailure { AppLog.w(TAG, "The saved back stack could not be read", it) }
                    .getOrNull()
            }
            val start = restoredStack(root, saved)
            AppLog.d(TAG, "attach: root $root, saved $saved → $start")
            return AppNavigator(start).also { navigator ->
                registry.registerSavedStateProvider(STATE_KEY) {
                    encodeToSavedState(serializer, navigator.backStack.toList())
                }
            }
        }

        /**
         * The saved stack if it starts at [root], without a library switch's folder picker (its in-memory state is
         * gone after a restart); otherwise only [root], because the sign-in state decides where the app starts.
         */
        fun restoredStack(root: Screen, saved: List<Screen>?): List<Screen> {
            if (saved.isNullOrEmpty() || saved.first() != root) return listOf(root)
            return listOf(root) + saved.drop(1).filter { it != Screen.FolderPicker && it != root }
        }

        /** The first app frames outside this class, e.g. "AppNavDisplayKt…invoke < NavDisplayKt…". */
        private fun caller(): String = Throwable().stackTrace
            .dropWhile { it.className.startsWith(AppNavigator::class.java.name) }
            .take(3)
            .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
    }
}
