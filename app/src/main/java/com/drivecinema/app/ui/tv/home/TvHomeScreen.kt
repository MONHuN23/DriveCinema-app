package com.drivecinema.app.ui.tv.home

import com.drivecinema.app.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import coil.compose.AsyncImage
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.player.ContinueWatching
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.ui.mobile.home.MobileHomeUiState
import com.drivecinema.app.ui.tv.TvInitialFocus
import com.drivecinema.app.ui.tv.TvReturnFocus
import com.drivecinema.app.ui.tv.rememberTvReturnFocus

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow

/** TV Home's layout (UI task ui/tv-browse-naming), in dp on the TV's 960 × 540 screen. */
private object TvHomeLayout {
    /** Where the spotlight starts, under the tabs. */
    val SpotlightTop = 88.dp
    val SpotlightWidth = 520.dp
    /** Where the rows start, under the spotlight; the focused row scrolls up to here. */
    val RowsTop = 300.dp
    /** A focused card sits this far below the rows' top: its row title stays above it. */
    val RowPivot = 40.dp
    /** The backdrop's left edge: it sits behind the right of the spotlight and fades in from the left. */
    val BackdropStart = 300.dp
    val BackdropHeight = 380.dp
    /** Home's posters, smaller than the grids' 150 dp so a poster row fits under the spotlight. */
    val PosterWidth = 120.dp
    /** The rows without the focus. */
    const val DimmedRowAlpha = 0.45f
}

/**
 * TV Home (UI task ui/tv-browse-naming, a calmer layout of the same parts): tabs at the top (Home, Films, Series,
 * Search, then the account chip), the spotlight for the focused card over a backdrop on the right, and the rows below
 * it. The focused row scrolls up under the spotlight, the other rows are dimmed, and the cards carry no text (the
 * spotlight names the focused one). The first card starts focused; Back from a card, a tab or Settings focuses it
 * again (Task M.3).
 *
 * Focus moves recompose only the cards involved and the spotlight (Task M.1): the spotlight and the backdrop change
 * once focus rests, and the dimming is read in the draw phase.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvHomeScreen(
    state: MobileHomeUiState,
    onMovieClick: (MediaItemEntity) -> Unit,
    onSeriesClick: (SeriesEntity) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onFilmsHeaderClick: () -> Unit,
    onSeriesHeaderClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAccountClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val backdrop = remember { TvAmbientBackdropState() }
    val spotlight = remember(backdrop) { TvSpotlightState(backdrop) }
    val firstCardFocus = remember { FocusRequester() }
    val homeTabFocus = remember { FocusRequester() }
    // Back from a film, a series, a tab or Settings lands on what opened it (Task M.3); else on the first card.
    val returnFocus = rememberTvReturnFocus()
    // The row that has the focus (-1: none, the tabs have it): the others are dimmed.
    val focusedRow = remember { mutableIntStateOf(-1) }

    TvInitialFocus(returnFocus) {
        if (!runCatching { firstCardFocus.requestFocus() }.getOrDefault(false)) {
            runCatching { homeTabFocus.requestFocus() }
        }
    }

    val onCardFocus = { key: String ->
        { focused: Boolean, backdropPath: String? ->
            backdrop.onCardFocused(focused, backdropPath)
            if (focused) spotlight.onFocused(key)
        }
    }
    // The rows keep the default horizontal scrolling; only the column pivots.
    val rowScrolling = LocalBringIntoViewSpec.current
    val pivot = with(LocalDensity.current) { TvHomeLayout.RowPivot.toPx() }
    val columnScrolling = remember(pivot) { TvRowPivotSpec(pivot) }
    fun firstFocus(row: Int, index: Int) = if (row == 0 && index == 0) Modifier.focusRequester(firstCardFocus) else Modifier

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DcColors.Canvas)
            .trackDpadHold(backdrop)
    ) {
        // Ambient backdrop of the focused card, once focus has rested (Task M.1), on the right behind the spotlight.
        TvAmbientBackdrop(
            state = backdrop,
            height = TvHomeLayout.BackdropHeight,
            topScrimColor = Color(0x590C0D10),
            label = "tv_home_backdrop_crossfade",
            modifier = Modifier.padding(start = TvHomeLayout.BackdropStart),
            sideFade = true
        )

        TvSpotlight(
            state = spotlight,
            home = state,
            modifier = Modifier
                .padding(start = 48.dp, top = TvHomeLayout.SpotlightTop)
                .width(TvHomeLayout.SpotlightWidth)
        )

        TvHomeTopBar(
            state = state,
            returnFocus = returnFocus,
            homeTabFocus = homeTabFocus,
            onFilmsClick = onFilmsHeaderClick,
            onSeriesClick = onSeriesHeaderClick,
            onSearchClick = onSearchClick,
            onAccountClick = onAccountClick
        )

        CompositionLocalProvider(LocalBringIntoViewSpec provides columnScrolling) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = TvHomeLayout.RowsTop)
                    .testTag("tv_home_rows"),
                // Room below, so the last row can scroll up under the spotlight too.
                contentPadding = PaddingValues(bottom = 240.dp)
            ) {
                var row = 0
                if (state.continueWatching.isNotEmpty()) {
                    val thisRow = row++
                    homeRow(thisRow, "Continue Watching", focusedRow, rowScrolling) {
                        itemsIndexed(state.continueWatching, key = { _, item -> item.id }) { index, item ->
                            val key = TvHomeKeys.continueWatching(item.id)
                            TvContinueWatchingCard(
                                title = item.title,
                                subtitle = ContinueWatching.subtitle(item),
                                posterPath = item.posterPath,
                                backdropPath = item.backdropPath,
                                progressFraction = if (item.durationMs > 0) item.positionMs.toFloat() / item.durationMs else 0f,
                                onClick = {
                                    returnFocus.opened(key)
                                    onContinueWatchingClick(item)
                                },
                                onFocusChange = onCardFocus(key),
                                showText = false,
                                modifier = returnFocus.modifierFor(key)
                                    .then(firstFocus(thisRow, index))
                                    .testTag("tv_home_card_$key")
                            )
                        }
                    }
                }
                if (state.movies.isNotEmpty()) {
                    val thisRow = row++
                    homeRow(thisRow, "Films", focusedRow, rowScrolling) {
                        itemsIndexed(state.movies, key = { _, movie -> movie.id }) { index, movie ->
                            val key = TvHomeKeys.film(movie.id)
                            TvPosterCard(
                                title = movie.title,
                                posterPath = movie.posterPath,
                                backdropPath = movie.backdropPath,
                                onClick = {
                                    returnFocus.opened(key)
                                    onMovieClick(movie)
                                },
                                onFocusChange = onCardFocus(key),
                                width = TvHomeLayout.PosterWidth,
                                showTitle = false,
                                modifier = returnFocus.modifierFor(key)
                                    .then(firstFocus(thisRow, index))
                                    .testTag("tv_home_card_$key")
                            )
                        }
                    }
                }
                if (state.series.isNotEmpty()) {
                    val thisRow = row++
                    homeRow(thisRow, "Series", focusedRow, rowScrolling) {
                        itemsIndexed(state.series, key = { _, show -> show.id }) { index, show ->
                            val key = TvHomeKeys.series(show.id)
                            TvPosterCard(
                                title = show.title,
                                posterPath = show.posterPath,
                                backdropPath = show.backdropPath,
                                onClick = {
                                    returnFocus.opened(key)
                                    onSeriesClick(show)
                                },
                                onFocusChange = onCardFocus(key),
                                width = TvHomeLayout.PosterWidth,
                                showTitle = false,
                                modifier = returnFocus.modifierFor(key)
                                    .then(firstFocus(thisRow, index))
                                    .testTag("tv_home_card_$key")
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One of Home's rows: its title and its cards, at full strength while it has the focus (or, with the focus on the
 * tabs, while it is the first row) and dimmed otherwise.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.homeRow(
    index: Int,
    title: String,
    focusedRow: MutableIntState,
    rowScrolling: BringIntoViewSpec,
    cards: LazyListScope.() -> Unit
) {
    item(key = "row_$title") {
        Column(
            modifier = Modifier
                .padding(bottom = 28.dp)
                .onFocusChanged {
                    if (it.hasFocus) focusedRow.intValue = index
                    else if (focusedRow.intValue == index) focusedRow.intValue = -1
                }
                // Read in the draw phase: moving between rows redraws them, it recomposes nothing.
                .graphicsLayer {
                    val lit = focusedRow.intValue == index || (focusedRow.intValue == -1 && index == 0)
                    alpha = if (lit) 1f else TvHomeLayout.DimmedRowAlpha
                }
                .testTag("tv_home_row_$index")
        ) {
            Text(
                text = title,
                color = DcColors.Text1,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 10.dp)
            )
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrolling) {
                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    content = cards
                )
            }
        }
    }
}

/** The tabs and the account chip across the top of Home. */
@Composable
private fun TvHomeTopBar(
    state: MobileHomeUiState,
    returnFocus: TvReturnFocus,
    homeTabFocus: FocusRequester,
    onFilmsClick: () -> Unit,
    onSeriesClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAccountClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "DriveCinema",
            color = DcColors.Brand,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.width(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TvHomeTab(
                label = "Home",
                selected = true,
                onClick = {},
                modifier = Modifier.focusRequester(homeTabFocus).testTag("tv_tab_home")
            )
            TvHomeTab(
                label = "Films",
                selected = false,
                onClick = {
                    returnFocus.opened(FILMS_TAB)
                    onFilmsClick()
                },
                modifier = returnFocus.modifierFor(FILMS_TAB).testTag("tv_tab_films")
            )
            TvHomeTab(
                label = "Series",
                selected = false,
                onClick = {
                    returnFocus.opened(SERIES_TAB)
                    onSeriesClick()
                },
                modifier = returnFocus.modifierFor(SERIES_TAB).testTag("tv_tab_series")
            )
            TvHomeTab(
                label = "Search",
                selected = false,
                icon = AppIcons.Search,
                onClick = {
                    returnFocus.opened(SEARCH_TAB)
                    onSearchClick()
                },
                modifier = returnFocus.modifierFor(SEARCH_TAB).testTag("tv_tab_search")
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (onAccountClick != null) {
            val label = state.account.displayName ?: state.account.email ?: "Account"
            // Back from Settings (Task P.2) puts the focus on the chip again.
            TvAccountChip(
                label = label,
                photoLink = state.account.photoLink,
                onClick = {
                    returnFocus.opened(ACCOUNT_CHIP)
                    onAccountClick()
                },
                modifier = returnFocus.modifierFor(ACCOUNT_CHIP)
            )
        }
    }
}

private const val FILMS_TAB = "tab:films"
private const val SERIES_TAB = "tab:series"
private const val SEARCH_TAB = "tab:search"
private const val ACCOUNT_CHIP = "chip:account"

@Composable
fun TvAccountChip(
    label: String,
    photoLink: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isFocused) Color(0xFF2A2E39) else DcColors.Surface2)
            .border(
                width = 2.dp,
                color = if (isFocused) Color.White else Color(0xFF2A2E39),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = onClick != null,
                onClick = { onClick?.invoke() }
            )
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick?.invoke()
                    true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!photoLink.isNullOrEmpty()) {
            AsyncImage(
                model = photoLink,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
            )
        } else {
            Icon(AppIcons.Person, contentDescription = null, tint = DcColors.Text1, modifier = Modifier.size(22.dp))
        }

        Text(
            text = label,
            color = if (isFocused) Color.White else DcColors.Text1,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
