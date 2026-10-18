package com.drivecinema.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How to prepare a library: folder layout, file naming, formats, sharing. Works on phone (touch scroll)
 * and TV: every section card is focusable, so the D-pad moves card by card and scrolls the list.
 */
@Composable
fun SetupGuideScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    sections: List<GuideSection> = SetupGuideContent.sections
) {
    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            backFocusRequester.requestFocus()
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GuideBackButton(onClick = onBackClick, modifier = Modifier.focusRequester(backFocusRequester))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Setup guide",
                            color = Color(0xFFF0F2F5),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "How to prepare your Google Drive so DriveCinema finds and recognises everything",
                            color = Color(0xFF9AA0A6),
                            fontSize = 13.sp
                        )
                    }
                }
            }
            items(sections, key = { it.title }) { section ->
                GuideSectionCard(section)
            }
        }
    }
}

@Composable
private fun GuideSectionCard(section: GuideSection) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16181D))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF4D88FF) else Color(0xFF22252C),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .padding(18.dp)
    ) {
        Text(
            text = section.title,
            color = Color(0xFFF0F2F5),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        section.intro?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = Color(0xFFD0D3D8), fontSize = 14.sp, lineHeight = 20.sp)
        }
        section.folderTree?.let { tree ->
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0C0D10))
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = tree,
                    color = Color(0xFF81C995),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
        if (section.bullets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            section.bullets.forEach { bullet ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(text = "•", color = Color(0xFF8AB4FF), fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = bullet, color = Color(0xFFD0D3D8), fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
        if (section.examples.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Examples — file name → what DriveCinema shows",
                color = Color(0xFF9AA0A6),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            section.examples.forEach { example ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0C0D10))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = example.fileName,
                        color = Color(0xFFF0F2F5),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "→ " + describe(example),
                        color = Color(0xFF81C995),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

internal fun describe(example: NamingExample): String = when (example) {
    is NamingExample.Movie -> if (example.year != null) "${example.title} (${example.year})" else example.title
    is NamingExample.Episode -> "Season ${example.season}, episode ${example.episode}"
}

@Composable
private fun GuideBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF2A2D35) else Color(0xFF1E2128))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF4D88FF) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "← Back", color = if (isFocused) Color.White else Color(0xFFB0B3B8), fontSize = 14.sp)
    }
}
