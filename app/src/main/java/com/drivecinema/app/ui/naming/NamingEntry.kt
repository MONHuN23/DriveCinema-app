package com.drivecinema.app.ui.naming

import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivecinema.app.core.naming.NamingTexts
import com.drivecinema.app.core.player.LocalIds

/** The phone's copy: the clipboard, and a short "Copied" where Android doesn't show its own (before 13). */
@Composable
private fun rememberCopy(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return { text ->
        clipboard.setText(AnnotatedString(text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, NamingTexts.COPIED, Toast.LENGTH_SHORT).show()
    }
}

/** Task R.2: the naming helper's back stack entry, with its entry-scoped [NamingHelperViewModel]. */
@Composable
fun NamingHelperEntry(deps: NamingDeps, isTv: Boolean, back: () -> Unit) {
    val viewModel = viewModel { NamingHelperViewModel(deps.tmdb, deps.tmdbAvailable) }
    val model = viewModel.model
    val state by model.state.collectAsStateWithLifecycle()
    val copy = rememberCopy()
    NamingHelperScreen(
        state = state,
        actions = NamingHelperActions(
            setQuery = model::setQuery,
            setKind = model::setKind,
            search = model::search,
            pickSeries = model::pickSeries,
            pickFilm = model::pickFilm,
            closePick = model::closePick,
            toggleSeason = model::toggleSeason,
            setLayout = model::setLayout,
            retrySeason = model::retrySeason,
            copy = copy,
            back = back
        ),
        isTv = isTv
    )
}

/** Task R.2: a series' Naming check entry; a series from a device folder (a `local:` id) gets the guide's pointer. */
@Composable
fun NamingCheckEntry(
    seriesId: String,
    seriesTitle: String,
    deps: NamingDeps,
    isTv: Boolean,
    openHelper: () -> Unit,
    back: () -> Unit
) {
    val viewModel = viewModel {
        NamingCheckViewModel(
            deps.tmdb,
            deps.tmdbAvailable,
            isDriveSeries = !seriesId.startsWith(LocalIds.PREFIX),
            listing = { deps.readSeriesFolder(seriesId) }
        )
    }
    val model = viewModel.model
    val state by model.state.collectAsStateWithLifecycle()
    val copy = rememberCopy()
    NamingCheckScreen(
        seriesTitle = seriesTitle,
        state = state,
        actions = NamingCheckActions(
            retry = model::load,
            setFilter = model::setFilter,
            toggleSeason = model::toggleSeason,
            pick = model::pick,
            copy = copy,
            openHelper = openHelper,
            back = back
        ),
        isTv = isTv
    )
}
