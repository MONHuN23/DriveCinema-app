package com.drivecinema.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import kotlinx.coroutines.launch

/** A control's colours at rest and while focused (design §3.4–3.5). */
@Immutable
data class DcControlColors(
    val container: Color,
    val content: Color,
    val focusedContainer: Color,
    val focusedContent: Color
)

/**
 * Milestone P: the one focus style of the phone/TV controls (design §3.5). It draws the control's container; when the
 * control is focused it scales it, fills it with [DcControlColors.focusedContainer], draws its content in
 * [DcControlColors.focusedContent] and adds a white ring with a small gap; pressed adds a light overlay.
 *
 * Everything happens in the draw phase, so moving focus recomposes nothing (Task M.1 found focus moves recomposing
 * the TV screen to be its main cost). Put it before the control's `clickable` (with `indication = null`) and give
 * both the same interaction source. [recolorContent] = false keeps the content's own colours (for images).
 */
data class DcFocusIndication(
    val colors: DcControlColors,
    val cornerRadius: Dp,
    val focusedScale: Float = DcTokens.Focus.CONTROL_SCALE,
    val recolorContent: Boolean = true
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        DcFocusNode(interactionSource, colors, cornerRadius, focusedScale, recolorContent)
}

private class DcFocusNode(
    private val interactionSource: InteractionSource,
    private val colors: DcControlColors,
    private val cornerRadius: Dp,
    private val focusedScale: Float,
    private val recolorContent: Boolean
) : Modifier.Node(), DrawModifierNode {

    /** 0 at rest, 1 focused. */
    private val focus = Animatable(0f)
    private var pressed = false
    private val tintPaint = Paint().apply { colorFilter = ColorFilter.tint(colors.focusedContent, BlendMode.SrcIn) }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> animateFocus(1f)
                    is FocusInteraction.Unfocus -> animateFocus(0f)
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                }
                invalidateDraw()
            }
        }
    }

    private fun animateFocus(target: Float) {
        coroutineScope.launch {
            focus.animateTo(target, tween(DcTokens.Focus.ANIMATION_MS)) { invalidateDraw() }
        }
    }

    override fun ContentDrawScope.draw() {
        val f = focus.value
        val radius = CornerRadius(cornerRadius.toPx())
        scale(1f + (focusedScale - 1f) * f) {
            drawRoundRect(lerp(colors.container, colors.focusedContainer, f), cornerRadius = radius)
            if (pressed) {
                drawRoundRect(Color.White.copy(alpha = DcTokens.PRESSED_OVERLAY_ALPHA), cornerRadius = radius)
            }
            if (recolorContent && f > 0.5f && colors.focusedContent != colors.content) {
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), tintPaint)
                this@draw.drawContent()
                drawContext.canvas.restore()
            } else {
                this@draw.drawContent()
            }
            if (f > 0f) {
                val ring = DcTokens.Focus.RING_WIDTH.dp.toPx()
                val outset = DcTokens.Focus.RING_GAP.dp.toPx() + ring / 2
                drawRoundRect(
                    color = DcColors.Focus.copy(alpha = f),
                    topLeft = Offset(-outset, -outset),
                    size = Size(size.width + 2 * outset, size.height + 2 * outset),
                    cornerRadius = CornerRadius(radius.x + outset),
                    style = Stroke(ring)
                )
            }
        }
    }
}
