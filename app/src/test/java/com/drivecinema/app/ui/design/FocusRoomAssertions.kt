package com.drivecinema.app.ui.design

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertTrue

/**
 * P.7: asserts that this focused element, grown by its focus scale and ring ([dcFocusOverflow]), lies inside the
 * scroll [container] in the vertical direction, where the scroll would clip it. An element taller than the container
 * can't fit and is skipped.
 */
fun SemanticsNodeInteraction.assertFocusFitsVertically(
    container: SemanticsNodeInteraction,
    scale: Float = DcTokens.Focus.CONTROL_SCALE,
    what: String = "focused element"
) {
    val item = getUnclippedBoundsInRoot()
    val box = container.getUnclippedBoundsInRoot()
    val overflow = dcFocusOverflow(item.height, scale)
    if (item.height + overflow * 2 > box.height) return
    val slack = 0.5.dp
    assertTrue("$what: top ${item.top} - $overflow is above ${box.top}", item.top - overflow >= box.top - slack)
    assertTrue("$what: bottom ${item.bottom} + $overflow is below ${box.bottom}", item.bottom + overflow <= box.bottom + slack)
}
