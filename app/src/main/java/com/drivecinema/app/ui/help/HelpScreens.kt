package com.drivecinema.app.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcChevron
import com.drivecinema.app.ui.design.DcGroup
import com.drivecinema.app.ui.design.DcGroupDivider
import com.drivecinema.app.ui.design.DcGroupTitle
import com.drivecinema.app.ui.design.DcListRow
import com.drivecinema.app.ui.design.DcStep
import com.drivecinema.app.ui.design.DcTopBar
import com.drivecinema.app.ui.setup.GuidePlatform
import com.drivecinema.app.ui.setup.GuideSection
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.app.ui.setup.SetupGuideContent

/** What the Help home says under its title. */
const val HELP_INTRO = "How to set up your library, use your devices and fix problems."

/**
 * Task P.4: the Help Center entries of the back stack, the Help home ([topicId] null) or one topic. The phone opens
 * a topic on top of the home and shows the next topic in its place; the TV shows both in one two-pane screen. A topic
 * this platform doesn't have (an old saved back stack) shows the home instead.
 */
@Composable
fun HelpEntry(
    topicId: String?,
    isTv: Boolean,
    open: (id: String) -> Unit,
    show: (id: String) -> Unit,
    back: () -> Unit
) {
    if (isTv) {
        TvHelpScreen(initialTopicId = topicId)
        return
    }
    val topic = topicId?.let { SetupGuideContent.topic(GuidePlatform.PHONE, it) }
    if (topic == null) {
        HelpHomeScreen(GuidePlatform.PHONE, onTopic = open, onBack = back)
    } else {
        val next = remember(topic.id) { SetupGuideContent.neighbour(GuidePlatform.PHONE, topic.id, 1) }
        HelpTopicScreen(topic, next, onNext = show, onBack = back)
    }
}

/** The phone's Help home: the platform's topics in their groups, each with a one-line summary. */
@Composable
fun HelpHomeScreen(platform: GuidePlatform, onTopic: (id: String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val groups = remember(platform) { SetupGuideContent.categoriesFor(platform) }
    Column(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
        DcTopBar(SettingsLabels.HELP_CENTER, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("help_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp)
        ) {
            item(key = "intro", contentType = "text") {
                Text(HELP_INTRO, style = DcTheme.type.body, color = DcColors.Text2)
            }
            groups.forEach { (category, topics) ->
                item(key = category.name, contentType = "group") {
                    DcGroupTitle(category.label)
                    DcGroup {
                        topics.forEachIndexed { index, topic ->
                            if (index > 0) DcGroupDivider()
                            DcListRow(
                                topic.title,
                                supporting = topic.summary,
                                icon = helpIcon(topic.id),
                                onClick = { onTopic(topic.id) },
                                modifier = Modifier.testTag("help_topic_${topic.id}"),
                                trailing = { DcChevron() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The phone's topic page: group, title, intro, the picture, numbered steps, "Good to know" and the next topic. */
@Composable
fun HelpTopicScreen(
    topic: GuideSection,
    next: GuideSection?,
    onNext: (id: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
        DcTopBar(SettingsLabels.HELP_CENTER, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("help_topic_page"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "head", contentType = "head") { TopicHead(topic) }
            if (topic.illustration != null) {
                item(key = "illustration", contentType = "illustration") { HelpIllustration(topic) }
            }
            if (topic.steps.isNotEmpty()) {
                item(key = "steps", contentType = "steps") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        topic.steps.forEachIndexed { index, step -> DcStep(index + 1, step, isHeading = false) }
                    }
                }
            }
            if (topic.tips.isNotEmpty()) {
                item(key = "tips", contentType = "tips") {
                    Column {
                        DcGroupTitle(GOOD_TO_KNOW)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { topic.tips.forEach { Tip(it) } }
                    }
                }
            }
            if (next != null) {
                item(key = "next", contentType = "next") {
                    Column(Modifier.navigationBarsPadding()) {
                        DcGroupTitle("Next")
                        DcGroup {
                            DcListRow(
                                next.title,
                                supporting = next.summary,
                                icon = helpIcon(next.id),
                                onClick = { onNext(next.id) },
                                modifier = Modifier.testTag("help_next"),
                                trailing = { DcChevron() }
                            )
                        }
                    }
                }
            }
        }
    }
}

const val GOOD_TO_KNOW = "Good to know"

/** The topic's group, title, where it applies ("Phone and TV only") and its intro. */
@Composable
internal fun TopicHead(topic: GuideSection, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            topic.category.label.uppercase(),
            style = DcTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = DcColors.Text3
        )
        Text(topic.title, style = DcTheme.type.headline, color = DcColors.Text1)
        topic.platformNote?.let { Text(it, style = DcTheme.type.caption, color = DcColors.Text3) }
        topic.intro?.let { Text(it, style = DcTheme.type.body, color = DcColors.Text2, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** One "Good to know" sentence, after a small dot. */
@Composable
internal fun Tip(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).background(DcColors.Text3, CircleShape))
        Text(text, style = DcTheme.type.body, color = DcColors.Text2, modifier = Modifier.weight(1f))
    }
}
