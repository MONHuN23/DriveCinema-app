package com.drivecinema.app.ui.tv.grid

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.tv.home.TvPosterCard
import com.drivecinema.app.ui.tv.home.TvPosterCardDefaults

object TvCollectionGridDefaults {
    const val ColumnCount: Int = 6
    val BackgroundCanvas = Color(0xFF0C0D10)
    val FocusBorderColor = Color(0xFF4D88FF)
}

data class TvGridItem(
    val id: String,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?
)

@Composable
fun TvCollectionGridScreen(
    title: String,
    items: List<TvGridItem>,
    onItemClick: (TvGridItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeBackdropPath by remember { mutableStateOf<String?>(null) }
    val sortedItems = remember(items) { items.sortedBy { it.title.lowercase() } }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(sortedItems) {
        if (sortedItems.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvCollectionGridDefaults.BackgroundCanvas)
    ) {
        // Ambient Backdrop Crossfade in upper background
        Crossfade(
            targetState = activeBackdropPath,
            animationSpec = tween(durationMillis = 300),
            label = "tv_grid_backdrop_crossfade"
        ) { backdrop ->
            if (!backdrop.isNullOrEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/w1280$backdrop",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(450.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(450.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x660C0D10),
                                        TvCollectionGridDefaults.BackgroundCanvas
                                    )
                                )
                            )
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvBackButton(onClick = onBackClick)

                Spacer(modifier = Modifier.width(20.dp))

                Text(
                    text = title,
                    color = Color(0xFFF0F2F5),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "(${sortedItems.size})",
                    color = Color(0xFF9AA0A6),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // 6-Column A-Z Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(TvCollectionGridDefaults.ColumnCount),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(sortedItems, key = { _, item -> item.id }) { index, item ->
                    val itemModifier = if (index == 0) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }

                    TvPosterCard(
                        title = item.title,
                        posterPath = item.posterPath,
                        backdropPath = item.backdropPath,
                        onClick = { onItemClick(item) },
                        onFocusChange = { isFocused, path ->
                            if (isFocused && path != null) {
                                activeBackdropPath = path
                            }
                        },
                        modifier = itemModifier
                    )
                }
            }
        }
    }
}

@Composable
fun TvBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF2A2D35) else Color(0xFF16181D))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) TvCollectionGridDefaults.FocusBorderColor else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "← Back",
            color = if (isFocused) Color.White else Color(0xFFB0B3B8),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
