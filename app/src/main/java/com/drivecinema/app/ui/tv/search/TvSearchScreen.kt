package com.drivecinema.app.ui.tv.search

import androidx.compose.foundation.layout.size
import com.drivecinema.app.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.search.SearchUiState
import com.drivecinema.app.ui.tv.TvInitialFocus
import com.drivecinema.app.ui.tv.rememberTvReturnFocus
import com.drivecinema.app.ui.tv.grid.TvCollectionGridDefaults
import com.drivecinema.app.ui.tv.grid.TvGridItem
import com.drivecinema.app.ui.tv.home.TvPosterCard
import com.drivecinema.app.ui.tv.home.TvAmbientBackdrop
import com.drivecinema.app.ui.tv.home.TvAmbientBackdropState
import com.drivecinema.app.ui.tv.home.trackDpadHold

@Composable
fun TvSearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onMovieClick: (MediaItemEntity) -> Unit,
    onSeriesClick: (SeriesEntity) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backdrop = remember { TvAmbientBackdropState() }
    val searchInputFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Convert matching movies and series into TvGridItems
    val combinedItems = remember(state.movies, state.series) {
        val movieList = state.movies.map { movie ->
            TvGridItem(
                id = movie.id,
                title = movie.title,
                posterPath = movie.posterPath,
                backdropPath = movie.backdropPath
            )
        }
        val seriesList = state.series.map { series ->
            TvGridItem(
                id = series.id,
                title = series.title,
                posterPath = series.posterPath,
                backdropPath = series.backdropPath
            )
        }
        (movieList + seriesList).sortedBy { it.title.lowercase() }
    }

    // Back from a result lands on its card (Task M.3); a first visit starts in the text field.
    val returnFocus = rememberTvReturnFocus()
    TvInitialFocus(returnFocus) {
        searchInputFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
            .trackDpadHold(backdrop)
    ) {
        // Ambient backdrop of the focused result, once focus has rested (Task M.1)
        TvAmbientBackdrop(
            state = backdrop,
            height = 450.dp,
            topScrimColor = Color(0x550C0D10),
            label = "tv_search_backdrop_crossfade"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp)
        ) {
            // TV Header & Remote Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // TV Back Button
                TvActionButton(
                    text = "← Back",
                    onClick = onBackClick
                )

                // TV Search Text Field
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = "Search films and series by title...",
                            color = Color(0xFF9AA0A6),
                            fontSize = 16.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            AppIcons.Search,
                            contentDescription = null,
                            tint = Color(0xFF9AA0A6),
                            modifier = Modifier.padding(start = 12.dp).size(22.dp)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E222A),
                        unfocusedContainerColor = Color(0xFF16181D),
                        disabledContainerColor = Color(0xFF16181D),
                        focusedTextColor = Color(0xFFF0F2F5),
                        unfocusedTextColor = Color(0xFFF0F2F5),
                        cursorColor = Color(0xFF4D88FF),
                        focusedIndicatorColor = Color(0xFF4D88FF),
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchInputFocusRequester)
                )

                // Clear Button
                if (state.query.isNotEmpty()) {
                    TvActionButton(
                        text = "Clear",
                        onClick = onClearQuery
                    )
                }
            }

            // Loading indicator
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 4.dp)
                        .height(3.dp),
                    color = Color(0xFF4D88FF),
                    trackColor = Color(0xFF16181D)
                )
            } else {
                Spacer(modifier = Modifier.height(11.dp))
            }

            // Results count / Status message
            if (state.query.isNotEmpty() && !state.isNoResults) {
                Text(
                    text = "${combinedItems.size} results for \"${state.query}\"",
                    color = Color(0xFF9AA0A6),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp)
                )
            }

            // Body: Empty vs No Results vs Results Grid
            when {
                state.isNoResults -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No films or series match \"${state.query}\"",
                                color = Color(0xFFF0F2F5),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Check spelling or search for another movie or television show.",
                                color = Color(0xFF9AA0A6),
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                combinedItems.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No media indexed in your library.",
                            color = Color(0xFF9AA0A6),
                            fontSize = 18.sp
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(TvCollectionGridDefaults.ColumnCount),
                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRestorer()
                    ) {
                        itemsIndexed(combinedItems, key = { _, item -> item.id }) { index, item ->
                            TvPosterCard(
                                title = item.title,
                                posterPath = item.posterPath,
                                backdropPath = item.backdropPath,
                                onClick = {
                                    returnFocus.opened(item.id)
                                    val movie = state.movies.find { it.id == item.id }
                                    if (movie != null) {
                                        onMovieClick(movie)
                                    } else {
                                        val series = state.series.find { it.id == item.id }
                                        if (series != null) {
                                            onSeriesClick(series)
                                        }
                                    }
                                },
                                onFocusChange = backdrop::onCardFocused,
                                modifier = returnFocus.modifierFor(item.id)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF4D88FF) else Color(0xFF16181D))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.White else Color(0xFFF0F2F5),
            fontSize = 15.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}
