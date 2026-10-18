package com.drivecinema.app.ui.setup

import android.net.Uri
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.core.database.dao.MediaSourceDao
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType

data class DuplicateCandidate(val mediaItemId: String, val label: String)

data class DuplicateReview(
    val sourceKey: String,
    val fileName: String,
    val localLabel: String,
    val candidates: List<DuplicateCandidate>
)

object DuplicateReviews {
    fun label(item: MediaItemEntity): String = when (item.type) {
        MediaType.MOVIE -> if (item.year != null) "${item.title} (${item.year})" else item.title
        MediaType.EPISODE -> "S%02dE%02d · %s".format(item.seasonNumber ?: 1, item.episodeNumber ?: 1, item.title)
    }

    /** Readable file name from a SAF document URI (".../document/primary%3AMovies%2FDune.mkv" → "Dune.mkv"). */
    fun fileName(sourceKey: String): String {
        val decoded = Uri.decode(Uri.parse(sourceKey).lastPathSegment ?: sourceKey)
        return decoded.substringAfterLast('/').substringAfterLast(':')
    }

    suspend fun load(dao: MediaSourceDao): List<DuplicateReview> =
        dao.getPendingMatches().mapNotNull { pending ->
            val source = dao.getSource(pending.sourceKey) ?: return@mapNotNull null
            val local = dao.getItemsByIds(listOf(source.mediaItemId)).firstOrNull()
            val candidates = dao.getItemsByIds(pending.candidateItemIds.split(',').filter { it.isNotBlank() })
                .filter { it.id != source.mediaItemId }
                .map { DuplicateCandidate(it.id, label(it)) }
            if (candidates.isEmpty()) return@mapNotNull null
            DuplicateReview(
                sourceKey = pending.sourceKey,
                fileName = fileName(pending.sourceKey),
                localLabel = local?.let { label(it) } ?: fileName(pending.sourceKey),
                candidates = candidates
            )
        }
}

/** Lets the user decide on local copies that might be the same film/episode as an existing title. */
@Composable
fun DuplicatesScreen(
    reviews: List<DuplicateReview>,
    onSameTitle: (sourceKey: String, mediaItemId: String) -> Unit,
    onKeepSeparate: (sourceKey: String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            backFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReviewButton("← Back", primary = false, onClick = onBackClick, modifier = Modifier.focusRequester(backFocus))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Possible duplicates", color = Color(0xFFF0F2F5), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "A file in your local folder looks like a title you already have. Same title → it plays the " +
                                "local copy and shares watch progress.",
                            color = Color(0xFF9AA0A6),
                            fontSize = 13.sp
                        )
                    }
                }
            }
            if (reviews.isEmpty()) {
                item(key = "empty") {
                    Text("Nothing to review.", color = Color(0xFFB0B3B8), fontSize = 15.sp)
                }
            }
            items(reviews, key = { it.sourceKey }) { review ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF16181D))
                        .border(1.dp, Color(0xFF22252C), RoundedCornerShape(12.dp))
                        .padding(18.dp)
                ) {
                    Text("Local file", color = Color(0xFF9AA0A6), fontSize = 12.sp)
                    Text(review.fileName, color = Color(0xFFF0F2F5), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Is it the same as:", color = Color(0xFFD0D3D8), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    review.candidates.forEach { candidate ->
                        ReviewButton(
                            text = "Yes — ${candidate.label}",
                            primary = true,
                            onClick = { onSameTitle(review.sourceKey, candidate.mediaItemId) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    ReviewButton(
                        text = "No — keep \"${review.localLabel}\" separate",
                        primary = false,
                        onClick = { onKeepSeparate(review.sourceKey) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color(0xFF2B4C8C)
                    primary -> Color(0x334D88FF)
                    else -> Color(0xFF1E2128)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (primary) Color(0xFF4D88FF) else Color(0xFF2A2E39),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, color = if (isFocused) Color.White else if (primary) Color(0xFF8AB4FF) else Color(0xFFB0B3B8), fontSize = 14.sp)
    }
}
