package com.drivecinema.app.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcFocusRoom
import com.drivecinema.app.ui.design.DcFocusRoomScrolling
import com.drivecinema.app.ui.design.DcControlColors
import com.drivecinema.app.ui.design.DcFocusIndication
import com.drivecinema.app.ui.design.DcListRow
import com.drivecinema.app.ui.design.DcStep
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.setup.GuidePlatform
import com.drivecinema.app.ui.setup.GuideSection
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.app.ui.setup.SetupGuideContent
import kotlinx.coroutines.launch

/** A block of the right pane: the overview (intro and picture), a step or a tip. */
private sealed interface HelpBlock {
    data object Overview : HelpBlock
    data class Step(val number: Int, val text: String) : HelpBlock
    data class Tip(val text: String) : HelpBlock
}

private fun blocksOf(topic: GuideSection): List<HelpBlock> = buildList {
    if (topic.intro != null || topic.illustration != null) add(HelpBlock.Overview)
    topic.steps.forEachIndexed { index, step -> add(HelpBlock.Step(index + 1, step)) }
    topic.tips.forEach { add(HelpBlock.Tip(it)) }
}

/** A block's look: a card that gets a lighter fill and the white ring when focused; text keeps its colours. */
private val blockColors = DcControlColors(
    container = DcColors.Surface1,
    content = DcColors.Text1,
    focusedContainer = DcColors.Surface3,
    focusedContent = DcColors.Text1
)

/**
 * Task P.4: the TV's Help Center, two panes (decision D-P4): the topics in their groups on the left, the selected
 * topic on the right. It replaces J.7's topic list above one long page. The selection follows the focus; Right or OK
 * enters the topic, where the intro, each step and each tip take one focus stop, so the remote scrolls block by block;
 * Left or Back returns to the topic in the list; Back from the list leaves the Help Center.
 */
@Composable
fun TvHelpScreen(initialTopicId: String?, modifier: Modifier = Modifier) {
    val groups = remember { SetupGuideContent.categoriesFor(GuidePlatform.TV) }
    val topics = remember { groups.flatMap { it.second } }
    var selectedId by rememberSaveable {
        mutableStateOf(initialTopicId?.takeIf { id -> topics.any { it.id == id } } ?: topics.first().id)
    }
    val selected = topics.first { it.id == selectedId }
    val topicFocus = remember { topics.associate { it.id to FocusRequester() } }
    val firstBlock = remember { FocusRequester() }
    var rightHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        for (attempt in 1..5) {
            if (runCatching { topicFocus.getValue(selectedId).requestFocus() }.getOrDefault(false)) break
            withFrameNanos { }
        }
    }
    BackHandler(enabled = rightHasFocus) { runCatching { topicFocus.getValue(selectedId).requestFocus() } }

    DcFocusRoomScrolling {
        Row(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .fillMaxHeight()
                    .background(DcColors.Surface1)
                    .padding(start = 48.dp, end = 20.dp, top = 34.dp, bottom = 27.dp)
            ) {
                Text(SettingsLabels.HELP_CENTER, style = DcTheme.type.headline, color = DcColors.Text1)
                Column(
                    modifier = Modifier
                        .testTag("tv_help_topics")
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = DcFocusRoom),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groups.forEach { (category, list) ->
                        Text(
                            category.label.uppercase(),
                            style = DcTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = DcColors.Text3,
                            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp)
                        )
                        list.forEach { topic ->
                            // The selection is drawn outside the row so the row's focus look stays the same node.
                            DcListRow(
                                topic.title,
                                icon = helpIcon(topic.id),
                                onClick = { runCatching { firstBlock.requestFocus() } },
                                modifier = Modifier
                                    .background(
                                        if (topic.id == selectedId) DcColors.Surface3 else Color.Transparent,
                                        RoundedCornerShape(DcTokens.Radius.CONTROL.dp)
                                    )
                                    .focusRequester(topicFocus.getValue(topic.id))
                                    .onFocusChanged { if (it.isFocused) selectedId = topic.id }
                                    .focusProperties { right = firstBlock }
                                    .testTag("tv_help_topic_${topic.id}")
                            )
                        }
                    }
                }
            }

            key(selected.id) {
                TopicPane(
                    selected,
                    firstBlock = firstBlock,
                    leftTo = topicFocus.getValue(selected.id),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { rightHasFocus = it.hasFocus }
                        .focusGroup()
                )
            }
        }
    }
}

/** The right pane: the topic's head, then its blocks, each one focus stop. */
@Composable
private fun TopicPane(topic: GuideSection, firstBlock: FocusRequester, leftTo: FocusRequester, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val blocks = remember(topic.id) { blocksOf(topic) }
    Column(
        modifier = modifier
            .testTag("tv_help_pane")
            .verticalScroll(scroll)
            .padding(start = 40.dp, end = 48.dp, top = 38.dp, bottom = 27.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            topic.category.label.uppercase(),
            style = DcTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = DcColors.Text3
        )
        Text(topic.title, style = DcTheme.type.headline, color = DcColors.Text1)
        topic.platformNote?.let { Text(it, style = DcTheme.type.caption, color = DcColors.Text3) }
        blocks.forEachIndexed { index, block ->
            if (block is HelpBlock.Tip && blocks.getOrNull(index - 1) !is HelpBlock.Tip) {
                Text(GOOD_TO_KNOW, style = DcTheme.type.title, color = DcColors.Text1, modifier = Modifier.padding(top = 10.dp))
            }
            Block(
                modifier = Modifier
                    .then(if (index == 0) Modifier.focusRequester(firstBlock) else Modifier)
                    .focusProperties {
                        left = leftTo
                        if (index == 0) up = FocusRequester.Cancel
                        if (index == blocks.lastIndex) down = FocusRequester.Cancel
                    }
                    // The first block shows the title above it again when the remote comes back up to it.
                    .onFocusChanged { if (index == 0 && it.isFocused) scope.launch { scroll.animateScrollTo(0) } }
                    .testTag("tv_help_block_$index")
            ) {
                when (block) {
                    HelpBlock.Overview -> {
                        topic.intro?.let { Text(it, style = DcTheme.type.body, color = DcColors.Text1) }
                        HelpIllustration(topic)
                    }
                    is HelpBlock.Step -> DcStep(block.number, block.text, isHeading = false)
                    is HelpBlock.Tip -> Tip(block.text)
                }
            }
        }
    }
}

/** One focusable block: no scale (it is large), a lighter fill and the white ring when focused, drawn without recomposing. */
@Composable
private fun Block(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .widthIn(max = 860.dp)
            .fillMaxWidth()
            .indication(
                interactionSource,
                DcFocusIndication(blockColors, DcTokens.Radius.CARD.dp, focusedScale = 1f, recolorContent = false)
            )
            .focusable(interactionSource = interactionSource)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
