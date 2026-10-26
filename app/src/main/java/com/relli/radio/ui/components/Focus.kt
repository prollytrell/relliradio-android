package com.relli.radio.ui.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.launch

/**
 * What a selected item looks like. On a TV remote (or a keyboard) the focused item gets a
 * gold outline and a soft glow, so you always know where you are. Touches still get the ripple.
 */
object FocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FocusNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 42
}

private class FocusNode(private val source: InteractionSource) : DelegatingNode(), DrawModifierNode {
    private var focused = false

    init {
        delegate(ripple(color = Relli.cream).create(source))
    }

    override fun onAttach() {
        coroutineScope.launch {
            val focuses = mutableListOf<Interaction>()
            source.interactions.collect { i ->
                when (i) {
                    is FocusInteraction.Focus -> focuses += i
                    is FocusInteraction.Unfocus -> focuses -= i.focus
                    is PressInteraction.Press -> Unit
                }
                val now = focuses.isNotEmpty()
                if (now != focused) { focused = now; invalidateDraw() }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focused) {
            drawRect(Relli.gold.copy(alpha = 0.18f))
            val w = 3.dp.toPx()
            drawRect(Relli.gold, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w))
        }
    }
}

/** Puts [FocusIndication] in place for everything inside. */
@Composable
fun FocusRings(content: @Composable () -> Unit) {
    CompositionLocalProvider(androidx.compose.foundation.LocalIndication provides FocusIndication, content = content)
}

/** Round icon button that shows the same focus highlight as everything else. */
@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.size(48.dp).clip(CircleShape)
            .clickable(interactionSource = source, indication = FocusIndication, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** True on Android TV / Google TV. Screens use it to swap touch-only controls for remote-friendly ones. */
val LocalIsTv = staticCompositionLocalOf { false }

/**
 * A slider that works by touch, mouse, keyboard and TV remote. Select it with the remote, then
 * Left/Right (or Up/Down for [vertical]) move it one step. The other arrows still move between controls.
 */
@Composable
fun RemoteSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    vertical: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    sliderModifier: Modifier = Modifier,
) {
    val span = valueRange.endInclusive - valueRange.start
    val step = if (steps > 0) span / (steps + 1) else span / 20f
    fun nudge(dir: Int) {
        var v = (value + dir * step).coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps > 0) v = (valueRange.start + Math.round((v - valueRange.start) / step) * step).coerceIn(valueRange.start, valueRange.endInclusive)
        onValueChange(v)
        onValueChangeFinished?.invoke()
    }
    val more = if (vertical) Key.DirectionUp else Key.DirectionRight
    val less = if (vertical) Key.DirectionDown else Key.DirectionLeft
    Box(
        modifier
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false
                else if (e.key == more) { nudge(1); true }
                else if (e.key == less) { nudge(-1); true }
                else false
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = FocusIndication) {},
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps,
            onValueChangeFinished = onValueChangeFinished, colors = colors,
            // The box above takes the remote's focus; the slider itself is for touch and mouse.
            modifier = sliderModifier.focusProperties { canFocus = false },
        )
    }
}
