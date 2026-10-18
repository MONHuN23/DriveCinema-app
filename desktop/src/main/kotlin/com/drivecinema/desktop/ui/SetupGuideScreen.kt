package com.drivecinema.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.ui.setup.GuideSection
import com.drivecinema.app.ui.setup.NamingExample
import com.drivecinema.app.ui.setup.SetupGuideContent

/** The same setup guide as on the phone and TV (shared content, checked against the real file-name parser). */
@Composable
fun SetupGuideScreen(onBack: () -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(DesktopColors.Background)) {
        Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.width(760.dp).padding(vertical = 32.dp)) {
                OutlinedButton(onClick = onBack) { Text("←  Back  (Esc)", color = DesktopColors.TextPrimary) }
                Spacer(Modifier.height(24.dp))
                Text("Setup guide", color = DesktopColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "How to prepare your Google Drive so DriveCinema finds your films and series.",
                    color = DesktopColors.TextSecondary,
                    fontSize = 15.sp
                )
                SetupGuideContent.sections.forEach { section ->
                    Spacer(Modifier.height(28.dp))
                    GuideSectionView(section)
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

@Composable
private fun GuideSectionView(section: GuideSection) {
    Text(section.title, color = DesktopColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    section.intro?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = DesktopColors.TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
    }
    section.bullets.forEach { bullet ->
        Spacer(Modifier.height(6.dp))
        Row {
            Text("•", color = DesktopColors.Accent, modifier = Modifier.width(18.dp))
            Text(bullet, color = DesktopColors.TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
    section.folderTree?.let { tree ->
        Spacer(Modifier.height(12.dp))
        SelectionContainer {
            Text(
                tree,
                color = DesktopColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DesktopColors.Surface, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            )
        }
    }
    if (section.examples.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().background(DesktopColors.Surface, RoundedCornerShape(8.dp)).padding(16.dp)
        ) {
            section.examples.forEach { example ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    SelectionContainer {
                        Text(
                            example.fileName,
                            color = DesktopColors.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.width(380.dp)
                        )
                    }
                    Text("→  ${describe(example)}", color = DesktopColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun describe(example: NamingExample): String = when (example) {
    is NamingExample.Movie -> example.title + (example.year?.let { " ($it)" } ?: "")
    is NamingExample.Episode -> "Season ${example.season}, episode ${example.episode}"
}
