package com.drivecinema.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.setup.GuideCategory
import com.drivecinema.app.ui.setup.GuideIcon
import com.drivecinema.app.ui.setup.GuideIllustration
import com.drivecinema.app.ui.setup.GuidePlatform
import com.drivecinema.app.ui.setup.GuideSection
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.app.ui.setup.SetupGuideContent
import com.drivecinema.app.ui.setup.shownAs
import com.drivecinema.desktop.ui.design.DesktopGroup
import com.drivecinema.desktop.ui.design.DesktopListRow
import com.drivecinema.desktop.ui.design.DesktopSideItem

/**
 * Task P.4: the desktop's Help Center (was one long setup guide page): the desktop's topics in their groups in a
 * sidebar, the selected topic at most 720 dp wide. Up and Down move through the topics; "Problems" are fold-outs.
 * The text is the same shared content as on the phone and TV, checked against the real file-name parser.
 */
@Composable
fun HelpCenterScreen(onBack: () -> Unit, initialTopicId: String? = null) {
    val groups = remember { SetupGuideContent.categoriesFor(GuidePlatform.DESKTOP) }
    val topics = remember { groups.flatMap { it.second } }
    var selectedId by rememberSaveable(initialTopicId) {
        mutableStateOf(initialTopicId?.takeIf { id -> topics.any { it.id == id } } ?: topics.first().id)
    }
    val selected = topics.first { it.id == selectedId }
    val keys = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keys.requestFocus() } }

    Row(
        Modifier
            .fillMaxSize()
            .background(DesktopColors.Background)
            .focusRequester(keys)
            .focusable()
            .onKeyEvent { event ->
                val offset = when (event.key) {
                    Key.DirectionDown -> 1
                    Key.DirectionUp -> -1
                    else -> return@onKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent true
                SetupGuideContent.neighbour(GuidePlatform.DESKTOP, selectedId, offset)?.let { selectedId = it.id }
                true
            }
    ) {
        Column(
            Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(DesktopColors.Surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            BackButton(onBack)
            Text(
                SettingsLabels.HELP_CENTER,
                style = DesktopType.headline,
                color = DesktopColors.TextPrimary,
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 4.dp)
            )
            groups.forEach { (category, list) ->
                Text(
                    category.label.uppercase(),
                    style = DesktopType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = DesktopColors.TextTertiary,
                    modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp)
                )
                list.forEach { topic ->
                    DesktopSideItem(topic.title, isSelected = topic.id == selectedId, onClick = { selectedId = topic.id })
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            key(selected.id) {
                val scroll = rememberScrollState()
                Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
                    TopicPage(
                        selected,
                        next = SetupGuideContent.neighbour(GuidePlatform.DESKTOP, selected.id, 1),
                        onNext = { selectedId = it },
                        modifier = Modifier.widthIn(max = 720.dp).padding(horizontal = 40.dp, vertical = 36.dp)
                    )
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scroll),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun TopicPage(topic: GuideSection, next: GuideSection?, onNext: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            topic.category.label.uppercase(),
            style = DesktopType.caption.copy(fontWeight = FontWeight.SemiBold),
            color = DesktopColors.TextTertiary
        )
        Text(topic.title, style = DesktopType.display, color = DesktopColors.TextPrimary)
        topic.platformNote?.let { Text(it, style = DesktopType.caption, color = DesktopColors.TextTertiary) }
        topic.intro?.let { Text(it, style = DesktopType.body, color = DesktopColors.TextSecondary) }
        topic.illustration?.let { Illustration(topic, it) }
        topic.steps.forEachIndexed { index, step -> Step(index + 1, step) }
        if (topic.tips.isNotEmpty()) {
            Text(
                if (topic.category == GuideCategory.PROBLEMS) "Problems and fixes" else "Good to know",
                style = DesktopType.title,
                color = DesktopColors.TextPrimary,
                modifier = Modifier.padding(top = 10.dp)
            )
            topic.tips.forEach { tip ->
                val problem = SetupGuideContent.problemAndFix(tip)
                if (topic.category == GuideCategory.PROBLEMS && problem != null) FoldOut(problem.first, problem.second) else Tip(tip)
            }
        }
        if (next != null) {
            Text("Next", style = DesktopType.caption, color = DesktopColors.TextTertiary, modifier = Modifier.padding(top = 16.dp))
            DesktopGroup {
                DesktopListRow(
                    next.title,
                    supporting = next.summary,
                    onClick = { onNext(next.id) },
                    trailing = { Icon(DesktopIcons.ChevronRight, contentDescription = null, tint = DesktopColors.TextSecondary) }
                )
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(28.dp).background(DesktopColors.Accent, CircleShape), contentAlignment = Alignment.Center) {
            Text("$number", style = DesktopType.label, color = DesktopColors.OnAccent)
        }
        SelectionContainer(Modifier.weight(1f).padding(top = 3.dp)) {
            Text(text, style = DesktopType.body, color = DesktopColors.TextPrimary)
        }
    }
}

@Composable
private fun Tip(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).background(DesktopColors.TextTertiary, CircleShape))
        SelectionContainer(Modifier.weight(1f)) {
            Text(text, style = DesktopType.body, color = DesktopColors.TextSecondary)
        }
    }
}

/** A problem as a heading that unfolds its fix (the desktop's troubleshooting, design §5). */
@Composable
private fun FoldOut(problem: String, fix: String) {
    var open by rememberSaveable(problem) { mutableStateOf(false) }
    DesktopGroup {
        DesktopListRow(
            problem,
            onClick = { open = !open },
            trailing = {
                Icon(
                    DesktopIcons.ExpandMore,
                    contentDescription = if (open) "Fold" else "Unfold",
                    tint = DesktopColors.TextSecondary,
                    modifier = Modifier.rotate(if (open) 180f else 0f)
                )
            }
        )
        if (open) {
            SelectionContainer {
                Text(
                    fix,
                    style = DesktopType.body,
                    color = DesktopColors.TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun Illustration(topic: GuideSection, illustration: GuideIllustration) {
    Column(
        Modifier.fillMaxWidth().background(DesktopColors.Surface, RoundedCornerShape(DcTokens.Radius.CARD.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (illustration) {
            GuideIllustration.Folders -> SetupGuideContent.folderTreeLines.forEach { line ->
                Row(
                    Modifier.padding(start = (line.depth * 20).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (line.isFolder) DesktopIcons.Folder else DesktopIcons.Movie,
                        contentDescription = null,
                        tint = if (line.isFolder) Color(DcTokens.Colors.RATING_STAR) else DesktopColors.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    SelectionContainer {
                        Text(line.name, style = DesktopType.body, color = if (line.isFolder) DesktopColors.TextPrimary else DesktopColors.TextSecondary)
                    }
                }
            }

            GuideIllustration.FileNames -> {
                Text("File name → what DriveCinema shows", style = DesktopType.caption, color = DesktopColors.TextTertiary)
                topic.examples.forEach { example ->
                    Row(Modifier.padding(top = 4.dp)) {
                        SelectionContainer {
                            Text(
                                example.fileName,
                                style = DesktopType.body,
                                fontFamily = FontFamily.Monospace,
                                color = DesktopColors.TextPrimary,
                                modifier = Modifier.width(380.dp)
                            )
                        }
                        Text("→  ${example.shownAs}", style = DesktopType.body, color = DesktopColors.Success)
                    }
                }
            }

            is GuideIllustration.Flow -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    illustration.nodes.forEachIndexed { index, node ->
                        if (index > 0) {
                            Icon(
                                DesktopIcons.ChevronRight,
                                contentDescription = null,
                                tint = DesktopColors.TextTertiary,
                                modifier = Modifier.padding(top = 14.dp).size(22.dp)
                            )
                        }
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier.size(48.dp).background(DesktopColors.SurfaceSelected, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(node.icon.vector, contentDescription = null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(26.dp))
                            }
                            Text(node.label, style = DesktopType.caption, color = DesktopColors.TextSecondary, textAlign = TextAlign.Center)
                        }
                    }
                }
                illustration.caption?.let {
                    Text(
                        it,
                        style = DesktopType.caption,
                        color = DesktopColors.TextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

private val GuideIcon.vector: ImageVector
    get() = when (this) {
        GuideIcon.PHONE -> DesktopIcons.Smartphone
        GuideIcon.TV -> DesktopIcons.Tv
        GuideIcon.PC -> DesktopIcons.DesktopWindows
        GuideIcon.WIFI -> DesktopIcons.Wifi
        GuideIcon.VPN -> DesktopIcons.VpnKey
        GuideIcon.CLOUD -> DesktopIcons.CloudUpload
        GuideIcon.FOLDER -> DesktopIcons.Folder
        GuideIcon.LOCK -> DesktopIcons.Lock
    }
