package com.drivecinema.app.ui.naming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivecinema.app.core.metadata.NamingGuide
import com.drivecinema.app.core.metadata.SeriesFolderListing
import com.drivecinema.app.core.metadata.TmdbClient
import com.drivecinema.app.core.naming.NamingCheckModel
import com.drivecinema.app.core.naming.NamingGuideModel

/**
 * Tasks R.2: the naming helper's screen state, kept by its back stack entry (a rotation keeps the search, the pick and
 * the loaded seasons; popping the screen drops the TMDB cache). The logic is `:shared`'s [NamingGuideModel].
 */
class NamingHelperViewModel(tmdb: TmdbClient, tmdbAvailable: Boolean) : ViewModel() {
    val model = NamingGuideModel(NamingGuide(tmdb), viewModelScope, tmdbAvailable)
}

/** Task R.2: a series' Naming check, entry-scoped like [NamingHelperViewModel]; it checks once, when created. */
class NamingCheckViewModel(
    tmdb: TmdbClient,
    tmdbAvailable: Boolean,
    isDriveSeries: Boolean,
    listing: suspend () -> SeriesFolderListing
) : ViewModel() {
    val model = NamingCheckModel(viewModelScope, tmdb, tmdbAvailable, isDriveSeries, listing).also { it.load() }
}

/** What the naming screens need from the activity: TMDB, and a series folder read with the signed-in account. */
class NamingDeps(
    val tmdb: TmdbClient,
    val tmdbAvailable: Boolean,
    /** Reads Drive series folder [seriesFolderId] (its name and videos) off the main thread; throws when it can't. */
    val readSeriesFolder: suspend (seriesFolderId: String) -> SeriesFolderListing
)
