package com.drivecinema.app.ui.mobile.search

import com.drivecinema.app.ui.icons.AppIcons
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.mobile.home.PosterCard
import com.drivecinema.app.ui.search.SearchUiState

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onMovieClick: (MediaItemEntity) -> Unit,
    onSeriesClick: (SeriesEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
    ) {
        // Search Input Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = "Search films and series by title...",
                        color = Color(0xFF9AA0A6),
                        fontSize = 15.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        AppIcons.Search,
                        contentDescription = null,
                        tint = Color(0xFF9AA0A6),
                        modifier = Modifier.padding(start = 8.dp).size(22.dp)
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = onClearQuery) {
                            Icon(AppIcons.Close, contentDescription = "Clear", tint = Color(0xFF9AA0A6))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF16181D),
                    unfocusedContainerColor = Color(0xFF16181D),
                    disabledContainerColor = Color(0xFF16181D),
                    focusedTextColor = Color(0xFFF0F2F5),
                    unfocusedTextColor = Color(0xFFF0F2F5),
                    cursorColor = Color(0xFF4D88FF),
                    focusedIndicatorColor = Color(0xFF4D88FF),
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Loading indicator
        if (state.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color(0xFF4D88FF),
                trackColor = Color(0xFF16181D)
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Empty state vs Results vs No Results
        when {
            state.isNoResults -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No Results Found",
                            color = Color(0xFFF0F2F5),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No films or series match \"${state.query}\".\nCheck spelling or try a different title.",
                            color = Color(0xFF9AA0A6),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            state.movies.isEmpty() && state.series.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No items in library.",
                        color = Color(0xFF9AA0A6),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                // Films and series together, A–Z, as on the TV: one list is quicker to scan than two tabs.
                val results = remember(state.movies, state.series) {
                    (state.movies.map { SearchResult.Film(it) } + state.series.map { SearchResult.Show(it) })
                        .sortedBy { it.title.lowercase() }
                }
                if (!state.isEmptyQuery) {
                    Text(
                        text = "${results.size} results for \"${state.query}\"",
                        color = Color(0xFF9AA0A6),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { it.key }, contentType = { "poster" }) { result ->
                        PosterCard(
                            title = result.title,
                            posterPath = result.posterPath,
                            onClick = {
                                when (result) {
                                    is SearchResult.Film -> onMovieClick(result.movie)
                                    is SearchResult.Show -> onSeriesClick(result.series)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** One search result card: a film or a series (their IDs could repeat across the two, so the key says which). */
private sealed interface SearchResult {
    val key: String
    val title: String
    val posterPath: String?

    data class Film(val movie: MediaItemEntity) : SearchResult {
        override val key get() = "film:${movie.id}"
        override val title get() = movie.title
        override val posterPath get() = movie.posterPath
    }

    data class Show(val series: SeriesEntity) : SearchResult {
        override val key get() = "series:${series.id}"
        override val title get() = series.title
        override val posterPath get() = series.posterPath
    }
}
